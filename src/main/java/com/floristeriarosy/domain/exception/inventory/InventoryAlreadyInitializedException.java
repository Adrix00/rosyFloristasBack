package com.floristeriarosy.domain.exception.inventory;

import com.floristeriarosy.domain.exception.ConflictException;
import com.floristeriarosy.domain.exception.HasErrorCode;

/**
 * A second {@code INITIAL} movement was attempted for a product: {@code ux_stock_movements_initial}
 * already has a row for it (inventory.md, section 3.2, section 9).
 */
public final class InventoryAlreadyInitializedException extends ConflictException implements HasErrorCode {

  /**
   * @param message a message for a person; never exposed raw to the API client
   */
  public InventoryAlreadyInitializedException(String message) {
    super(message);
  }

  @Override
  public String errorCode() {
    return InventoryErrorCode.INVENTORY_ALREADY_INITIALIZED.name();
  }
}
