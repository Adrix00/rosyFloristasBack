package com.floristeriarosy.application.auth.port.in;

import com.floristeriarosy.application.auth.command.LogoutCommand;

/** Closes the session of the device that presented the cookie (auth.md, rule 3.7). Idempotent. */
public interface LogoutUseCase {

  /**
   * @param command the plaintext refresh token from the cookie, or {@code null} if absent
   */
  void execute(LogoutCommand command);
}
