package com.floristeriarosy.application.admin.service;

import com.floristeriarosy.application.admin.command.ResetAdminTotpCommand;
import com.floristeriarosy.application.admin.port.in.ResetAdminTotpUseCase;
import com.floristeriarosy.application.admin.port.out.AdminReadPort;
import com.floristeriarosy.application.admin.port.out.AdminWritePort;
import com.floristeriarosy.application.auth.port.out.RevokeTokenFamilyPort;
import com.floristeriarosy.application.shared.dto.AuditAction;
import com.floristeriarosy.application.shared.port.out.AuditLogPort;
import com.floristeriarosy.domain.exception.admin.AdminNotFoundException;
import com.floristeriarosy.domain.model.admin.Admin;
import com.floristeriarosy.domain.model.admin.valueobject.AdminId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements {@link ResetAdminTotpUseCase}: the {@code OWNER} resets an admin's TOTP enrollment
 * to its initial state (admin.md, rule 3.5).
 *
 * <p>{@code OWNER}-only per admin.md rule 3.1; unenforced today, same tracked gap as {@link
 * CreateAdminService}.
 */
@Service
@Transactional
public class ResetAdminTotpService implements ResetAdminTotpUseCase {

  private static final String ENTITY_TYPE = "admin_user";

  private static final Logger LOGGER = LoggerFactory.getLogger(ResetAdminTotpService.class);

  private final AdminReadPort readPort;
  private final AdminWritePort writePort;
  private final RevokeTokenFamilyPort revokeTokenFamilyPort;
  private final AuditLogPort auditLogPort;

  /**
   * @param readPort loads the admin whose TOTP is reset
   * @param writePort persists the reset state
   * @param revokeTokenFamilyPort revokes every live session (ADR-008)
   * @param auditLogPort records the reset (ADR-010)
   */
  public ResetAdminTotpService(
      AdminReadPort readPort,
      AdminWritePort writePort,
      RevokeTokenFamilyPort revokeTokenFamilyPort,
      AuditLogPort auditLogPort) {
    this.readPort = readPort;
    this.writePort = writePort;
    this.revokeTokenFamilyPort = revokeTokenFamilyPort;
    this.auditLogPort = auditLogPort;
  }

  /**
   * Clears the TOTP secret, disables it and clears the replay-guard step, so the next login
   * re-enrolls TOTP (admin.md, rule 3.5). The password is untouched. Revokes every session the
   * admin holds — the typical reason for this reset is a lost or stolen device.
   *
   * @param command id of the admin whose TOTP is reset
   * @throws AdminNotFoundException {@code command.id()} does not exist
   */
  @Override
  public void execute(ResetAdminTotpCommand command) {
    LOGGER.debug("resetAdminTotp actorId={} id={}", command.actorId(), command.id());

    AdminId id = AdminId.of(command.id());
    Admin admin =
        readPort.findById(id).orElseThrow(() -> new AdminNotFoundException("Admin " + id + " not found"));

    admin.resetTotp();
    Admin saved = writePort.save(admin);

    revokeTokenFamilyPort.revokeAllForSubject(saved.id().value());
    auditLogPort.record(
        command.actorId(),
        AuditAction.UPDATE,
        ENTITY_TYPE,
        saved.id().value(),
        List.of("totpSecretEncrypted", "totpEnabled", "totpLastUsedStep"));

    LOGGER.debug("resetAdminTotp -> id={} reset", saved.id());
  }
}
