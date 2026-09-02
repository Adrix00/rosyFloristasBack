package com.floristeriarosy.infrastructure.config;

import com.floristeriarosy.application.admin.command.BootstrapOwnerCommand;
import com.floristeriarosy.application.admin.port.in.BootstrapOwnerUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Creates the first {@code OWNER} at startup, if none is active yet (admin.md, rule 3.9). Not a
 * Flyway migration: the password must pass through Argon2id, which lives in Java, not SQL.
 * Idempotent by construction — {@link BootstrapOwnerUseCase} no-ops if an active {@code OWNER}
 * already exists, so restarting the application has no effect.
 */
@Component
public class BootstrapOwnerRunner implements ApplicationRunner {

  private static final Logger LOGGER = LoggerFactory.getLogger(BootstrapOwnerRunner.class);

  private final BootstrapOwnerUseCase bootstrapOwnerUseCase;
  private final String ownerEmail;
  private final String ownerPassword;

  /**
   * @param bootstrapOwnerUseCase creates the first {@code OWNER}, if needed
   * @param ownerEmail {@code app.bootstrap.owner-email}, possibly blank
   * @param ownerPassword {@code app.bootstrap.owner-password}, possibly blank
   */
  public BootstrapOwnerRunner(
      BootstrapOwnerUseCase bootstrapOwnerUseCase,
      @Value("${app.bootstrap.owner-email:}") String ownerEmail,
      @Value("${app.bootstrap.owner-password:}") String ownerPassword) {
    this.bootstrapOwnerUseCase = bootstrapOwnerUseCase;
    this.ownerEmail = ownerEmail;
    this.ownerPassword = ownerPassword;
  }

  /**
   * @param args unused
   */
  @Override
  public void run(ApplicationArguments args) {
    LOGGER.debug("run");
    bootstrapOwnerUseCase.execute(new BootstrapOwnerCommand(ownerEmail, ownerPassword));
    LOGGER.debug("run -> done");
  }
}
