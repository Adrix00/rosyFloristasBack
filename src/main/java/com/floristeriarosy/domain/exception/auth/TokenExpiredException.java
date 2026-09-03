package com.floristeriarosy.domain.exception.auth;

import com.floristeriarosy.domain.exception.HasErrorCode;
import com.floristeriarosy.domain.exception.UnauthorizedException;

/** The refresh token family's absolute expiry has passed; a normal login is required (ADR-008). */
public final class TokenExpiredException extends UnauthorizedException implements HasErrorCode {

  /**
   * @param message a message for a person; never exposed raw to the API client
   */
  public TokenExpiredException(String message) {
    super(message);
  }

  @Override
  public String errorCode() {
    return AuthErrorCode.TOKEN_EXPIRED.name();
  }
}
