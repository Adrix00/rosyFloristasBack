package com.floristeriarosy.application.auth.port.in;

import com.floristeriarosy.application.auth.command.AdminLoginCommand;
import com.floristeriarosy.application.auth.dto.AdminLoginDto;
import com.floristeriarosy.domain.exception.auth.InvalidCredentialsException;

/** Step 1 of the admin login (auth.md, rule 3.3). */
public interface AdminLoginUseCase {

  /**
   * @param command email and password
   * @return the ephemeral {@code mfaToken} and whether TOTP enrollment is still pending
   * @throws InvalidCredentialsException email unknown, password wrong, or admin inactive
   */
  AdminLoginDto execute(AdminLoginCommand command);
}
