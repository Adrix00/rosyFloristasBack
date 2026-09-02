package com.floristeriarosy.application.admin.service;

import com.floristeriarosy.application.admin.command.BootstrapOwnerCommand;
import com.floristeriarosy.application.admin.port.in.BootstrapOwnerUseCase;
import com.floristeriarosy.application.admin.port.out.AdminReadPort;
import com.floristeriarosy.application.admin.port.out.AdminWritePort;
import com.floristeriarosy.application.auth.port.out.PasswordHasherPort;
import com.floristeriarosy.application.shared.port.out.PiiCryptoPort;
import com.floristeriarosy.domain.model.admin.Admin;
import com.floristeriarosy.domain.model.admin.AdminRole;
import com.floristeriarosy.domain.model.admin.valueobject.AdminId;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements {@link BootstrapOwnerUseCase}: creates the first {@code OWNER} at startup, if none is
 * active yet (admin.md, rule 3.9). No audit row is written — there is no acting admin, this runs
 * before any session ever existed.
 */
@Service
@Transactional
public class BootstrapOwnerService implements BootstrapOwnerUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(BootstrapOwnerService.class);

  private final AdminReadPort readPort;
  private final AdminWritePort writePort;
  private final PiiCryptoPort piiCryptoPort;
  private final PasswordHasherPort passwordHasherPort;

  /**
   * @param readPort checks whether an active {@code OWNER} already exists
   * @param writePort persists the first {@code OWNER}
   * @param piiCryptoPort encrypts and hashes the email (ADR-005)
   * @param passwordHasherPort hashes the provisional password (ADR-005)
   */
  public BootstrapOwnerService(
      AdminReadPort readPort,
      AdminWritePort writePort,
      PiiCryptoPort piiCryptoPort,
      PasswordHasherPort passwordHasherPort) {
    this.readPort = readPort;
    this.writePort = writePort;
    this.piiCryptoPort = piiCryptoPort;
    this.passwordHasherPort = passwordHasherPort;
  }

  /**
   * No-op if an active {@code OWNER} already exists (idempotent, admin.md rule 3.9's edge case
   * "restart has no effect"). Logs an error and returns, without throwing, if no active {@code
   * OWNER} exists and no bootstrap credentials were configured — the public store must keep
   * working even with an inaccessible admin panel.
   *
   * @param command the first {@code OWNER}'s email and provisional password, possibly blank
   */
  @Override
  public void execute(BootstrapOwnerCommand command) {
    LOGGER.debug("bootstrapOwner");

    if (readPort.countActiveOwners() > 0) {
      LOGGER.debug("bootstrapOwner -> an active OWNER already exists, no-op");
      return;
    }
    if (command.email() == null
        || command.email().isBlank()
        || command.password() == null
        || command.password().isBlank()) {
      LOGGER.error(
          "No active OWNER exists and BOOTSTRAP_OWNER_EMAIL/BOOTSTRAP_OWNER_PASSWORD are not set: "
              + "the admin panel is inaccessible until an OWNER is created");
      return;
    }

    String normalizedEmail = command.email().trim().toLowerCase(Locale.ROOT);
    Admin owner =
        Admin.create(
            AdminId.newId(),
            piiCryptoPort.encrypt(normalizedEmail),
            piiCryptoPort.hmac(normalizedEmail),
            passwordHasherPort.hash(command.password()),
            AdminRole.OWNER);
    Admin saved = writePort.save(owner);

    LOGGER.debug("bootstrapOwner -> id={} created", saved.id());
  }
}
