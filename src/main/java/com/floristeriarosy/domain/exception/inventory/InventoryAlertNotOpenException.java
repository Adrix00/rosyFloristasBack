package com.floristeriarosy.domain.exception.inventory;

import com.floristeriarosy.domain.exception.ConflictException;
import com.floristeriarosy.domain.exception.HasErrorCode;

/**
 * A resolve or dismiss was requested on an alert that is already {@code RESOLVED} or {@code
 * DISMISSED} (inventory.md, section 3.8, section 9). Both outcomes are terminal.
 */
public final class InventoryAlertNotOpenException extends ConflictException implements HasErrorCode {

  /**
   * @param message a message for a person; never exposed raw to the API client
   */
  public InventoryAlertNotOpenException(String message) {
    super(message);
  }

  @Override
  public String errorCode() {
    return InventoryErrorCode.INVENTORY_ALERT_NOT_OPEN.name();
  }
}
