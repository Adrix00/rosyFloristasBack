package com.floristeriarosy.domain.exception.auth;

import com.floristeriarosy.domain.exception.HasErrorCode;
import com.floristeriarosy.domain.exception.UnauthorizedException;

/**
 * Email or password incorrect, account archived, or admin inactive — the same response for all four
 * causes on purpose (auth.md, section 9; 00-security-validation-integrity.md, rule 7).
 */
public final class InvalidCredentialsException extends UnauthorizedException
    implements HasErrorCode {

  /**
   * @param message a message for a person; never exposed raw to the API client
   */
  public InvalidCredentialsException(String message) {
    super(message);
  }

  @Override
  public String errorCode() {
    return AuthErrorCode.INVALID_CREDENTIALS.name();
  }
}
