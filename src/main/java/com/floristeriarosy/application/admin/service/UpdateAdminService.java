package com.floristeriarosy.application.admin.service;

import com.floristeriarosy.application.admin.command.UpdateAdminCommand;
import com.floristeriarosy.application.admin.dto.AdminDto;
import com.floristeriarosy.application.admin.mapper.AdminDtoMapper;
import com.floristeriarosy.application.admin.port.in.UpdateAdminUseCase;
import com.floristeriarosy.application.admin.port.out.AdminReadPort;
import com.floristeriarosy.application.admin.port.out.AdminWritePort;
import com.floristeriarosy.application.shared.dto.AuditAction;
import com.floristeriarosy.application.shared.port.out.AuditLogPort;
import com.floristeriarosy.application.shared.port.out.PiiCryptoPort;
import com.floristeriarosy.domain.exception.admin.AdminEmailAlreadyExistsException;
import com.floristeriarosy.domain.exception.admin.AdminNotFoundException;
import com.floristeriarosy.domain.exception.admin.LastOwnerCannotBeRemovedException;
import com.floristeriarosy.domain.model.admin.Admin;
import com.floristeriarosy.domain.model.admin.AdminRole;
import com.floristeriarosy.domain.model.admin.valueobject.AdminId;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements {@link UpdateAdminUseCase}: full replace ({@code PUT}) of an admin's email and role.
 *
 * <p>{@code OWNER}-only per admin.md rule 3.1, enforced via {@code @PreAuthorize} (feature/auth,
 * phase 13).
 */
@Service
@Transactional
public class UpdateAdminService implements UpdateAdminUseCase {

  private static final String ENTITY_TYPE = "admin_user";

  private static final Logger LOGGER = LoggerFactory.getLogger(UpdateAdminService.class);

  private final AdminReadPort readPort;
  private final AdminWritePort writePort;
  private final PiiCryptoPort piiCryptoPort;
  private final AuditLogPort auditLogPort;
  private final AdminDtoMapper mapper;

  /**
   * @param readPort loads the admin being updated, and checks the new email for conflicts
   * @param writePort persists the updated admin
   * @param piiCryptoPort encrypts and hashes the new email (ADR-005)
   * @param auditLogPort records the update (ADR-010)
   * @param mapper builds the response DTO
   */
  public UpdateAdminService(
      AdminReadPort readPort,
      AdminWritePort writePort,
      PiiCryptoPort piiCryptoPort,
      AuditLogPort auditLogPort,
      AdminDtoMapper mapper) {
    this.readPort = readPort;
    this.writePort = writePort;
    this.piiCryptoPort = piiCryptoPort;
    this.auditLogPort = auditLogPort;
    this.mapper = mapper;
  }

  /**
   * Replaces email and role. Rejects a role change that would leave {@code admin_users} without any
   * active {@code OWNER} (admin.md, rule 3.7).
   *
   * @param command id of the admin to update, plus its new email and role
   * @return the updated admin
   * @throws AdminNotFoundException {@code command.id()} does not exist
   * @throws AdminEmailAlreadyExistsException the new email is already used by another admin
   * @throws LastOwnerCannotBeRemovedException {@code command.id()} is the last active {@code OWNER}
   *     and {@code command.role()} is {@code ADMIN}
   */
  @Override
  @PreAuthorize("hasRole('OWNER')")
  public AdminDto execute(UpdateAdminCommand command) {
    LOGGER.debug(
        "updateAdmin actorId={} id={} role={}", command.actorId(), command.id(), command.role());

    AdminId id = AdminId.of(command.id());
    Admin admin =
        readPort
            .findById(id)
            .orElseThrow(() -> new AdminNotFoundException("Admin " + id + " not found"));

    if (admin.role() == AdminRole.OWNER
        && admin.active()
        && command.role() == AdminRole.ADMIN
        && readPort.countActiveOwnersForUpdate() <= 1) {
      throw new LastOwnerCannotBeRemovedException("Cannot demote the last active OWNER " + id);
    }

    String normalizedEmail = normalize(command.email());
    byte[] emailHash = piiCryptoPort.hmac(normalizedEmail);
    if (!Arrays.equals(emailHash, admin.emailHash())) {
      readPort
          .findByEmailHash(emailHash)
          .filter(other -> !other.id().equals(id))
          .ifPresent(
              other -> {
                throw new AdminEmailAlreadyExistsException(
                    "An admin with this email already exists");
              });
    }

    admin.replace(piiCryptoPort.encrypt(normalizedEmail), emailHash, command.role());
    Admin saved = writePort.save(admin);
    auditLogPort.record(
        command.actorId(),
        AuditAction.UPDATE,
        ENTITY_TYPE,
        saved.id().value(),
        List.of("email", "role"));

    AdminDto result = mapper.toDto(saved);
    LOGGER.debug("updateAdmin -> id={}", result.id());
    return result;
  }

  /**
   * @param email the raw email from the request
   * @return {@code email}, trimmed and lower-cased
   */
  private String normalize(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }
}
