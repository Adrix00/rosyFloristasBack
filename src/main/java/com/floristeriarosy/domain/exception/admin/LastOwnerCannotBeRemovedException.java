package com.floristeriarosy.domain.exception.admin;

import com.floristeriarosy.domain.exception.ConflictException;
import com.floristeriarosy.domain.exception.HasErrorCode;

/**
 * The action would leave {@code admin_users} without any active {@code OWNER} (admin.md, rule
 * 3.7): deactivating or demoting the last active {@code OWNER}.
 */
public final class LastOwnerCannotBeRemovedException extends ConflictException
    implements HasErrorCode {

  /**
   * @param message a message for a person; never exposed raw to the API client
   */
  public LastOwnerCannotBeRemovedException(String message) {
    super(message);
  }

  @Override
  public String errorCode() {
    return AdminErrorCode.LAST_OWNER_CANNOT_BE_REMOVED.name();
  }
}
