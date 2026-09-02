package com.floristeriarosy.domain.exception.admin;

import com.floristeriarosy.domain.exception.HasErrorCode;
import com.floristeriarosy.domain.exception.NotFoundException;

/** The admin identifier does not exist. */
public final class AdminNotFoundException extends NotFoundException implements HasErrorCode {

  /**
   * @param message a message for a person; never exposed raw to the API client
   */
  public AdminNotFoundException(String message) {
    super(message);
  }

  @Override
  public String errorCode() {
    return AdminErrorCode.ADMIN_NOT_FOUND.name();
  }
}
