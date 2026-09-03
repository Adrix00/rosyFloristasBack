package com.floristeriarosy.domain.exception.auth;

import com.floristeriarosy.domain.exception.ConflictException;
import com.floristeriarosy.domain.exception.HasErrorCode;

/**
 * {@code POST /auth/admin/mfa} was called for an admin who never generated a TOTP secret (auth.md,
 * section 9).
 */
public final class TotpEnrollmentRequiredException extends ConflictException
    implements HasErrorCode {

  /**
   * @param message a message for a person; never exposed raw to the API client
   */
  public TotpEnrollmentRequiredException(String message) {
    super(message);
  }

  @Override
  public String errorCode() {
    return AuthErrorCode.TOTP_ENROLLMENT_REQUIRED.name();
  }
}
