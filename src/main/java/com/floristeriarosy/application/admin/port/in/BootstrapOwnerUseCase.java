package com.floristeriarosy.application.admin.port.in;

import com.floristeriarosy.application.admin.command.BootstrapOwnerCommand;

/**
 * Creates the very first {@code OWNER} at application startup, if none is active yet (admin.md,
 * rule 3.9). Triggered by {@code infrastructure.config.BootstrapOwnerRunner}, not by an HTTP
 * endpoint — nobody can call {@code POST /admin/users} without {@code OWNER} credentials, which is
 * exactly the chicken-and-egg problem this use case solves.
 */
public interface BootstrapOwnerUseCase {

  /**
   * @param command the first {@code OWNER}'s email and provisional password
   */
  void execute(BootstrapOwnerCommand command);
}
