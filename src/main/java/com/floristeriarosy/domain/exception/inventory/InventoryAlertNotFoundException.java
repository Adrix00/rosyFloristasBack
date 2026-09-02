package com.floristeriarosy.domain.exception.inventory;

import com.floristeriarosy.domain.exception.HasErrorCode;
import com.floristeriarosy.domain.exception.NotFoundException;

/** No inventory alert exists with the requested id (inventory.md, section 9). */
public final class InventoryAlertNotFoundException extends NotFoundException implements HasErrorCode {

  /**
   * @param message a message for a person; never exposed raw to the API client
   */
  public InventoryAlertNotFoundException(String message) {
    super(message);
  }

  @Override
  public String errorCode() {
    return InventoryErrorCode.INVENTORY_ALERT_NOT_FOUND.name();
  }
}
