package com.floristeriarosy.application.admin.service;

import com.floristeriarosy.application.admin.command.ResetAdminPasswordCommand;
import com.floristeriarosy.application.admin.dto.PasswordResetResult;
import com.floristeriarosy.application.admin.port.in.ResetAdminPasswordUseCase;
import com.floristeriarosy.application.admin.port.out.AdminReadPort;
import com.floristeriarosy.application.admin.port.out.AdminWritePort;
import com.floristeriarosy.application.auth.port.out.PasswordHasherPort;
import com.floristeriarosy.application.auth.port.out.RevokeTokenFamilyPort;
import com.floristeriarosy.application.shared.dto.AuditAction;
import com.floristeriarosy.application.shared.port.out.AuditLogPort;
import com.floristeriarosy.domain.exception.admin.AdminNotFoundException;
import com.floristeriarosy.domain.model.admin.Admin;
import com.floristeriarosy.domain.model.admin.valueobject.AdminId;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements {@link ResetAdminPasswordUseCase}: the {@code OWNER} fixes a new provisional password
 * (admin.md, rule 3.4).
 *
 * <p>{@code OWNER}-only per admin.md rule 3.1, enforced via {@code @PreAuthorize} (feature/auth,
 * phase 13).
 */
@Service
@Transactional
public class ResetAdminPasswordService implements ResetAdminPasswordUseCase {

  private static final String ENTITY_TYPE = "admin_user";
  private static final int GENERATED_PASSWORD_BYTES = 16;

  private static final Logger LOGGER = LoggerFactory.getLogger(ResetAdminPasswordService.class);

  private final AdminReadPort readPort;
  private final AdminWritePort writePort;
  private final PasswordHasherPort passwordHasherPort;
  private final RevokeTokenFamilyPort revokeTokenFamilyPort;
  private final AuditLogPort auditLogPort;
  private final SecureRandom secureRandom = new SecureRandom();

  /**
   * @param readPort loads the admin whose password is reset
   * @param writePort persists the new password hash
   * @param passwordHasherPort hashes the generated provisional password (ADR-005)
   * @param revokeTokenFamilyPort revokes every live session (ADR-008)
   * @param auditLogPort records the reset (ADR-010)
   */
  public ResetAdminPasswordService(
      AdminReadPort readPort,
      AdminWritePort writePort,
      PasswordHasherPort passwordHasherPort,
      RevokeTokenFamilyPort revokeTokenFamilyPort,
      AuditLogPort auditLogPort) {
    this.readPort = readPort;
    this.writePort = writePort;
    this.passwordHasherPort = passwordHasherPort;
    this.revokeTokenFamilyPort = revokeTokenFamilyPort;
    this.auditLogPort = auditLogPort;
  }

  /**
   * Generates a new random provisional password, hashes and stores it, sets {@code
   * password_change_required = true} and revokes every session the admin holds (admin.md, rule
   * 3.4).
   *
   * @param command id of the admin whose password is reset
   * @return the newly generated provisional password, in plaintext, once
   * @throws AdminNotFoundException {@code command.id()} does not exist
   */
  @Override
  @PreAuthorize("hasRole('OWNER')")
  public PasswordResetResult execute(ResetAdminPasswordCommand command) {
    LOGGER.debug("resetAdminPassword actorId={} id={}", command.actorId(), command.id());

    AdminId id = AdminId.of(command.id());
    Admin admin =
        readPort
            .findById(id)
            .orElseThrow(() -> new AdminNotFoundException("Admin " + id + " not found"));

    String temporaryPassword = generateTemporaryPassword();
    admin.resetPassword(passwordHasherPort.hash(temporaryPassword));
    Admin saved = writePort.save(admin);

    revokeTokenFamilyPort.revokeAllForSubject(saved.id().value());
    auditLogPort.record(
        command.actorId(),
        AuditAction.UPDATE,
        ENTITY_TYPE,
        saved.id().value(),
        List.of("passwordHash", "passwordChangeRequired"));

    LOGGER.debug("resetAdminPassword -> id={} reset", saved.id());
    return new PasswordResetResult(temporaryPassword);
  }

  /**
   * @return a random, URL-safe password well above {@code ValidPassword}'s minimum length and never
   *     in its common-password denylist by construction
   */
  private String generateTemporaryPassword() {
    byte[] randomBytes = new byte[GENERATED_PASSWORD_BYTES];
    secureRandom.nextBytes(randomBytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
  }
}
