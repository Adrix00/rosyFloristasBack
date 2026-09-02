package com.floristeriarosy.domain.exception.admin;

import com.floristeriarosy.domain.exception.HasErrorCode;
import com.floristeriarosy.domain.exception.UnprocessableException;

/** The new password is equal to the current one (admin.md, section 9). */
public final class PasswordUnchangedException extends UnprocessableException
    implements HasErrorCode {

  /**
   * @param message a message for a person; never exposed raw to the API client
   */
  public PasswordUnchangedException(String message) {
    super(message);
  }

  @Override
  public String errorCode() {
    return AdminErrorCode.PASSWORD_UNCHANGED.name();
  }
}
