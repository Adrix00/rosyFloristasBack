package com.floristeriarosy.domain.exception.inventory;

import com.floristeriarosy.domain.exception.ConflictException;
import com.floristeriarosy.domain.exception.HasErrorCode;

/** A stock movement was requested on a product with {@code stock = NULL} (inventory.md, section 3.7). */
public final class InventoryNotManagedException extends ConflictException implements HasErrorCode {

  /**
   * @param message a message for a person; never exposed raw to the API client
   */
  public InventoryNotManagedException(String message) {
    super(message);
  }

  @Override
  public String errorCode() {
    return InventoryErrorCode.INVENTORY_NOT_MANAGED.name();
  }
}
