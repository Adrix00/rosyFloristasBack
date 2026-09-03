package com.floristeriarosy.domain.exception.auth;

import com.floristeriarosy.domain.exception.ConflictException;
import com.floristeriarosy.domain.exception.HasErrorCode;

/**
 * {@code POST /auth/admin/totp/enrollment} was called for an admin whose TOTP is already enrolled
 * (auth.md, rule 3.4). The reset path is the {@code OWNER}-driven one in {@code admin.md}.
 */
public final class TotpAlreadyEnrolledException extends ConflictException implements HasErrorCode {

  /**
   * @param message a message for a person; never exposed raw to the API client
   */
  public TotpAlreadyEnrolledException(String message) {
    super(message);
  }

  @Override
  public String errorCode() {
    return AuthErrorCode.TOTP_ALREADY_ENROLLED.name();
  }
}
