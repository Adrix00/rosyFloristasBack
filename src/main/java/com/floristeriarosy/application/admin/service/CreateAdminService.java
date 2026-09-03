package com.floristeriarosy.application.admin.service;

import com.floristeriarosy.application.admin.command.CreateAdminCommand;
import com.floristeriarosy.application.admin.dto.AdminDto;
import com.floristeriarosy.application.admin.mapper.AdminDtoMapper;
import com.floristeriarosy.application.admin.port.in.CreateAdminUseCase;
import com.floristeriarosy.application.admin.port.out.AdminReadPort;
import com.floristeriarosy.application.admin.port.out.AdminWritePort;
import com.floristeriarosy.application.auth.port.out.PasswordHasherPort;
import com.floristeriarosy.application.shared.dto.AuditAction;
import com.floristeriarosy.application.shared.port.out.AuditLogPort;
import com.floristeriarosy.application.shared.port.out.PiiCryptoPort;
import com.floristeriarosy.domain.exception.admin.AdminEmailAlreadyExistsException;
import com.floristeriarosy.domain.model.admin.Admin;
import com.floristeriarosy.domain.model.admin.valueobject.AdminId;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements {@link CreateAdminUseCase}: creates an administrator with a provisional password
 * (admin.md, rule 3.2).
 *
 * <p>{@code OWNER}-only per admin.md rule 3.1, enforced via {@code @PreAuthorize} (feature/auth,
 * phase 13).
 */
@Service
@Transactional
public class CreateAdminService implements CreateAdminUseCase {

  private static final String ENTITY_TYPE = "admin_user";

  private static final Logger LOGGER = LoggerFactory.getLogger(CreateAdminService.class);

  private final AdminReadPort readPort;
  private final AdminWritePort writePort;
  private final PiiCryptoPort piiCryptoPort;
  private final PasswordHasherPort passwordHasherPort;
  private final AuditLogPort auditLogPort;
  private final AdminDtoMapper mapper;

  /**
   * @param readPort checks the normalized email is not already in use
   * @param writePort persists the new admin
   * @param piiCryptoPort encrypts and hashes the email (ADR-005)
   * @param passwordHasherPort hashes the provisional password (ADR-005)
   * @param auditLogPort records the creation (ADR-010)
   * @param mapper builds the response DTO, decrypting the email back for display
   */
  public CreateAdminService(
      AdminReadPort readPort,
      AdminWritePort writePort,
      PiiCryptoPort piiCryptoPort,
      PasswordHasherPort passwordHasherPort,
      AuditLogPort auditLogPort,
      AdminDtoMapper mapper) {
    this.readPort = readPort;
    this.writePort = writePort;
    this.piiCryptoPort = piiCryptoPort;
    this.passwordHasherPort = passwordHasherPort;
    this.auditLogPort = auditLogPort;
    this.mapper = mapper;
  }

  /**
   * Creates a new admin, born {@code active}, with TOTP not enrolled and {@code
   * password_change_required = true} (admin.md, rule 3.2).
   *
   * @param command email, provisional password and role of the admin to create
   * @return the created admin
   * @throws AdminEmailAlreadyExistsException the email is already used by another admin
   */
  @Override
  @PreAuthorize("hasRole('OWNER')")
  public AdminDto execute(CreateAdminCommand command) {
    LOGGER.debug("createAdmin actorId={} role={}", command.actorId(), command.role());

    String normalizedEmail = normalize(command.email());
    byte[] emailHash = piiCryptoPort.hmac(normalizedEmail);
    if (readPort.findByEmailHash(emailHash).isPresent()) {
      throw new AdminEmailAlreadyExistsException("An admin with this email already exists");
    }

    Admin admin =
        Admin.create(
            AdminId.newId(),
            piiCryptoPort.encrypt(normalizedEmail),
            emailHash,
            passwordHasherPort.hash(command.password()),
            command.role());
    Admin saved = writePort.save(admin);
    auditLogPort.record(
        command.actorId(),
        AuditAction.CREATE,
        ENTITY_TYPE,
        saved.id().value(),
        List.of("email", "role", "passwordHash"));

    AdminDto result = mapper.toDto(saved);
    LOGGER.debug("createAdmin -> id={}", result.id());
    return result;
  }

  /**
   * @param email the raw email from the request
   * @return {@code email}, trimmed and lower-cased (00-security-validation-integrity.md, section 4:
   *     normalize before hashing so equivalent inputs share one hash)
   */
  private String normalize(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }
}
