package com.floristeriarosy.application.auth.port.in;

import com.floristeriarosy.application.auth.command.EnrollAdminTotpCommand;
import com.floristeriarosy.application.auth.dto.TotpEnrollmentDto;
import com.floristeriarosy.domain.exception.auth.InvalidMfaTokenException;
import com.floristeriarosy.domain.exception.auth.TotpAlreadyEnrolledException;

/** Generates a new TOTP secret for the admin identified by the {@code mfaToken} (auth.md, 3.4). */
public interface EnrollAdminTotpUseCase {

  /**
   * @param command the {@code mfaToken}
   * @return the enrollment URI and secret, returned only this once
   * @throws InvalidMfaTokenException the token is missing, expired, or not a {@code mfa} token
   * @throws TotpAlreadyEnrolledException the admin already has TOTP enrolled
   */
  TotpEnrollmentDto execute(EnrollAdminTotpCommand command);
}
