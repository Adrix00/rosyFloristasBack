package com.floristeriarosy.domain.exception.admin;

import com.floristeriarosy.domain.exception.ConflictException;
import com.floristeriarosy.domain.exception.HasErrorCode;

/** The email is already used by another admin ({@code uq_admin_users_email_hash}). */
public final class AdminEmailAlreadyExistsException extends ConflictException
    implements HasErrorCode {

  /**
   * @param message a message for a person; never exposed raw to the API client
   */
  public AdminEmailAlreadyExistsException(String message) {
    super(message);
  }

  @Override
  public String errorCode() {
    return AdminErrorCode.ADMIN_EMAIL_ALREADY_EXISTS.name();
  }
}
