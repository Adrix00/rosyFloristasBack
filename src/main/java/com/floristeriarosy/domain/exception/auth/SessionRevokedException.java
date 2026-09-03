package com.floristeriarosy.domain.exception.auth;

import com.floristeriarosy.domain.exception.HasErrorCode;
import com.floristeriarosy.domain.exception.UnauthorizedException;

/**
 * A refresh token already revoked was presented again: reuse detected, the entire family is revoked
 * (auth.md, rule 3.6; ADR-008). The frontend must react with a full logout and a visible warning,
 * not a silent retry.
 */
public final class SessionRevokedException extends UnauthorizedException implements HasErrorCode {

  /**
   * @param message a message for a person; never exposed raw to the API client
   */
  public SessionRevokedException(String message) {
    super(message);
  }

  @Override
  public String errorCode() {
    return AuthErrorCode.SESSION_REVOKED.name();
  }
}
