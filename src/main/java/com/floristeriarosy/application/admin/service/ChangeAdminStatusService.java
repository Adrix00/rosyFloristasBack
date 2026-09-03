package com.floristeriarosy.application.admin.service;

import com.floristeriarosy.application.admin.command.ChangeAdminStatusCommand;
import com.floristeriarosy.application.admin.dto.AdminDto;
import com.floristeriarosy.application.admin.mapper.AdminDtoMapper;
import com.floristeriarosy.application.admin.port.in.ChangeAdminStatusUseCase;
import com.floristeriarosy.application.admin.port.out.AdminReadPort;
import com.floristeriarosy.application.admin.port.out.AdminWritePort;
import com.floristeriarosy.application.auth.port.out.RevokeTokenFamilyPort;
import com.floristeriarosy.application.shared.dto.AuditAction;
import com.floristeriarosy.application.shared.port.out.AuditLogPort;
import com.floristeriarosy.domain.exception.admin.AdminNotFoundException;
import com.floristeriarosy.domain.exception.admin.LastOwnerCannotBeRemovedException;
import com.floristeriarosy.domain.model.admin.Admin;
import com.floristeriarosy.domain.model.admin.AdminRole;
import com.floristeriarosy.domain.model.admin.valueobject.AdminId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements {@link ChangeAdminStatusUseCase}: activates or deactivates an admin (admin.md, rule
 * 3.6).
 *
 * <p>{@code OWNER}-only per admin.md rule 3.1, enforced via {@code @PreAuthorize} (feature/auth,
 * phase 13).
 */
@Service
@Transactional
public class ChangeAdminStatusService implements ChangeAdminStatusUseCase {

  private static final String ENTITY_TYPE = "admin_user";

  private static final Logger LOGGER = LoggerFactory.getLogger(ChangeAdminStatusService.class);

  private final AdminReadPort readPort;
  private final AdminWritePort writePort;
  private final RevokeTokenFamilyPort revokeTokenFamilyPort;
  private final AuditLogPort auditLogPort;
  private final AdminDtoMapper mapper;

  /**
   * @param readPort loads the admin whose status is changing, and counts active owners
   * @param writePort persists the new status
   * @param revokeTokenFamilyPort revokes every live session on deactivation (ADR-008)
   * @param auditLogPort records the change (ADR-010)
   * @param mapper builds the response DTO
   */
  public ChangeAdminStatusService(
      AdminReadPort readPort,
      AdminWritePort writePort,
      RevokeTokenFamilyPort revokeTokenFamilyPort,
      AuditLogPort auditLogPort,
      AdminDtoMapper mapper) {
    this.readPort = readPort;
    this.writePort = writePort;
    this.revokeTokenFamilyPort = revokeTokenFamilyPort;
    this.auditLogPort = auditLogPort;
    this.mapper = mapper;
  }

  /**
   * Activates or deactivates an admin. Rejects deactivating the last active {@code OWNER}
   * (admin.md, rule 3.7). Deactivating revokes every session the admin holds (rule 3.6).
   *
   * @param command id of the admin and the status to set
   * @return the admin with its new status
   * @throws AdminNotFoundException {@code command.id()} does not exist
   * @throws LastOwnerCannotBeRemovedException {@code command.id()} is the last active {@code OWNER}
   *     and {@code command.active()} is {@code false}
   */
  @Override
  @PreAuthorize("hasRole('OWNER')")
  public AdminDto execute(ChangeAdminStatusCommand command) {
    LOGGER.debug(
        "changeAdminStatus actorId={} id={} active={}",
        command.actorId(),
        command.id(),
        command.active());

    AdminId id = AdminId.of(command.id());
    Admin admin =
        readPort
            .findById(id)
            .orElseThrow(() -> new AdminNotFoundException("Admin " + id + " not found"));

    if (!command.active()
        && admin.role() == AdminRole.OWNER
        && admin.active()
        && readPort.countActiveOwnersForUpdate() <= 1) {
      throw new LastOwnerCannotBeRemovedException("Cannot deactivate the last active OWNER " + id);
    }

    if (command.active()) {
      admin.activate();
    } else {
      admin.deactivate();
    }
    Admin saved = writePort.save(admin);

    if (!command.active()) {
      revokeTokenFamilyPort.revokeAllForSubject(saved.id().value());
    }
    auditLogPort.record(
        command.actorId(), AuditAction.UPDATE, ENTITY_TYPE, saved.id().value(), List.of("active"));

    AdminDto result = mapper.toDto(saved);
    LOGGER.debug("changeAdminStatus -> id={} active={}", result.id(), result.active());
    return result;
  }
}
