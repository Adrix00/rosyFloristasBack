package com.floristeriarosy.domain.exception.auth;

import com.floristeriarosy.domain.exception.HasErrorCode;
import com.floristeriarosy.domain.exception.UnauthorizedException;

/**
 * The TOTP code is incorrect, outside the &plusmn;1 step window, or already consumed (auth.md, rule
 * 3.4; RFC 6238).
 */
public final class InvalidTotpCodeException extends UnauthorizedException implements HasErrorCode {

  /**
   * @param message a message for a person; never exposed raw to the API client
   */
  public InvalidTotpCodeException(String message) {
    super(message);
  }

  @Override
  public String errorCode() {
    return AuthErrorCode.INVALID_TOTP_CODE.name();
  }
}
