package com.floristeriarosy.domain.exception.auth;

import com.floristeriarosy.domain.exception.HasErrorCode;
import com.floristeriarosy.domain.exception.UnauthorizedException;

/**
 * The {@code mfaToken} is missing, expired, has an invalid signature, or does not carry {@code typ
 * = "mfa"} (auth.md, rule 3.3). Rejected exactly like an anonymous caller.
 */
public final class InvalidMfaTokenException extends UnauthorizedException implements HasErrorCode {

  /**
   * @param message a message for a person; never exposed raw to the API client
   */
  public InvalidMfaTokenException(String message) {
    super(message);
  }

  @Override
  public String errorCode() {
    return AuthErrorCode.INVALID_MFA_TOKEN.name();
  }
}
