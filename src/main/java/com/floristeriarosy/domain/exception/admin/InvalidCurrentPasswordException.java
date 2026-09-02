package com.floristeriarosy.domain.exception.admin;

import com.floristeriarosy.domain.exception.HasErrorCode;
import com.floristeriarosy.domain.exception.UnauthorizedException;

/** {@code currentPassword} does not match the admin's stored password (admin.md, section 9). */
public final class InvalidCurrentPasswordException extends UnauthorizedException
    implements HasErrorCode {

  /**
   * @param message a message for a person; never exposed raw to the API client
   */
  public InvalidCurrentPasswordException(String message) {
    super(message);
  }

  @Override
  public String errorCode() {
    return AdminErrorCode.INVALID_CURRENT_PASSWORD.name();
  }
}
