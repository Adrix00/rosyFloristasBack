package com.floristeriarosy.application.auth.port.in;

import com.floristeriarosy.application.auth.command.LogoutAllCommand;

/**
 * Closes every session of the subject that presented the cookie, this device included (auth.md,
 * rule 3.7). Idempotent.
 */
public interface LogoutAllUseCase {

  /**
   * @param command the plaintext refresh token from the cookie, or {@code null} if absent
   */
  void execute(LogoutAllCommand command);
}
