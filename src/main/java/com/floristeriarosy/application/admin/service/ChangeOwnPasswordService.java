package com.floristeriarosy.application.admin.service;

import com.floristeriarosy.application.admin.command.ChangeOwnPasswordCommand;
import com.floristeriarosy.application.admin.port.in.ChangeOwnPasswordUseCase;
import com.floristeriarosy.application.admin.port.out.AdminReadPort;
import com.floristeriarosy.application.admin.port.out.AdminWritePort;
import com.floristeriarosy.application.auth.port.out.PasswordHasherPort;
import com.floristeriarosy.application.shared.dto.AuditAction;
import com.floristeriarosy.application.shared.port.out.AuditLogPort;
import com.floristeriarosy.domain.exception.admin.AdminNotFoundException;
import com.floristeriarosy.domain.exception.admin.InvalidCurrentPasswordException;
import com.floristeriarosy.domain.exception.admin.PasswordUnchangedException;
import com.floristeriarosy.domain.model.admin.Admin;
import com.floristeriarosy.domain.model.admin.valueobject.AdminId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements {@link ChangeOwnPasswordUseCase}: an admin replaces their own password (admin.md,
 * section 4). Unlike an {@code OWNER}-driven reset, this does not revoke sessions — it is the
 * admin's own session doing this, not a response to a lost credential.
 */
@Service
@Transactional
public class ChangeOwnPasswordService implements ChangeOwnPasswordUseCase {

  private static final String ENTITY_TYPE = "admin_user";

  private static final Logger LOGGER = LoggerFactory.getLogger(ChangeOwnPasswordService.class);

  private final AdminReadPort readPort;
  private final AdminWritePort writePort;
  private final PasswordHasherPort passwordHasherPort;
  private final AuditLogPort auditLogPort;

  /**
   * @param readPort loads the admin changing their password
   * @param writePort persists the new password hash
   * @param passwordHasherPort verifies the current password and hashes the new one (ADR-005)
   * @param auditLogPort records the change (ADR-010)
   */
  public ChangeOwnPasswordService(
      AdminReadPort readPort,
      AdminWritePort writePort,
      PasswordHasherPort passwordHasherPort,
      AuditLogPort auditLogPort) {
    this.readPort = readPort;
    this.writePort = writePort;
    this.passwordHasherPort = passwordHasherPort;
    this.auditLogPort = auditLogPort;
  }

  /**
   * Verifies {@code currentPassword}, rejects a {@code newPassword} equal to it, then stores the
   * new password and clears {@code password_change_required} (admin.md, rule 3.4).
   *
   * @param command the admin's id, current password and new password
   * @throws AdminNotFoundException {@code command.adminId()} does not exist
   * @throws InvalidCurrentPasswordException {@code currentPassword} does not match
   * @throws PasswordUnchangedException {@code newPassword} equals {@code currentPassword}
   */
  @Override
  @PreAuthorize("hasRole('ADMIN')")
  public void execute(ChangeOwnPasswordCommand command) {
    LOGGER.debug("changeOwnPassword adminId={}", command.adminId());

    AdminId id = AdminId.of(command.adminId());
    Admin admin =
        readPort
            .findById(id)
            .orElseThrow(() -> new AdminNotFoundException("Admin " + id + " not found"));

    if (!passwordHasherPort.matches(command.currentPassword(), admin.passwordHash())) {
      throw new InvalidCurrentPasswordException("Current password does not match");
    }
    if (command.newPassword().equals(command.currentPassword())) {
      throw new PasswordUnchangedException("The new password must differ from the current one");
    }

    admin.changeOwnPassword(passwordHasherPort.hash(command.newPassword()));
    Admin saved = writePort.save(admin);
    auditLogPort.record(
        saved.id().value(),
        AuditAction.UPDATE,
        ENTITY_TYPE,
        saved.id().value(),
        List.of("passwordHash", "passwordChangeRequired"));

    LOGGER.debug("changeOwnPassword -> id={} changed", saved.id());
  }
}
