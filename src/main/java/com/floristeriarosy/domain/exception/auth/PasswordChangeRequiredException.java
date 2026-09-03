package com.floristeriarosy.domain.exception.auth;

import com.floristeriarosy.domain.exception.ForbiddenException;
import com.floristeriarosy.domain.exception.HasErrorCode;

/**
 * The session's password is still provisional; every endpoint is rejected except {@code POST
 * /admin/me/password} and {@code POST /auth/logout} (auth.md, rule 3.9).
 */
public final class PasswordChangeRequiredException extends ForbiddenException
    implements HasErrorCode {

  /**
   * @param message a message for a person; never exposed raw to the API client
   */
  public PasswordChangeRequiredException(String message) {
    super(message);
  }

  @Override
  public String errorCode() {
    return AuthErrorCode.PASSWORD_CHANGE_REQUIRED.name();
  }
}
