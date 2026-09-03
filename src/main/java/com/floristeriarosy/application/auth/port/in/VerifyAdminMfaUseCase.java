package com.floristeriarosy.application.auth.port.in;

import com.floristeriarosy.application.auth.command.VerifyAdminMfaCommand;
import com.floristeriarosy.application.auth.dto.AuthDto;
import com.floristeriarosy.domain.exception.auth.InvalidMfaTokenException;
import com.floristeriarosy.domain.exception.auth.InvalidTotpCodeException;
import com.floristeriarosy.domain.exception.auth.TotpEnrollmentRequiredException;

/** Step 2 of the admin login (auth.md, rule 3.3): confirms TOTP and issues the session. */
public interface VerifyAdminMfaUseCase {

  /**
   * @param command the {@code mfaToken} and the 6-digit code
   * @return the issued access and refresh tokens
   * @throws InvalidMfaTokenException the token is missing, expired, or not a {@code mfa} token
   * @throws TotpEnrollmentRequiredException the admin has not generated a TOTP secret yet
   * @throws InvalidTotpCodeException the code is wrong, out of window, or already consumed
   */
  AuthDto execute(VerifyAdminMfaCommand command);
}
