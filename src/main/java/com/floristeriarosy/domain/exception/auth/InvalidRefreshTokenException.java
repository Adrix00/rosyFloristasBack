package com.floristeriarosy.domain.exception.auth;

import com.floristeriarosy.domain.exception.HasErrorCode;
import com.floristeriarosy.domain.exception.UnauthorizedException;

/** The presented refresh token cookie does not correspond to any row (auth.md, rule 3.5). */
public final class InvalidRefreshTokenException extends UnauthorizedException
    implements HasErrorCode {

  /**
   * @param message a message for a person; never exposed raw to the API client
   */
  public InvalidRefreshTokenException(String message) {
    super(message);
  }

  @Override
  public String errorCode() {
    return AuthErrorCode.INVALID_REFRESH_TOKEN.name();
  }
}
