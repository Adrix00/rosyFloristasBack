package com.floristeriarosy.domain.exception.inventory;

import com.floristeriarosy.domain.exception.ConflictException;
import com.floristeriarosy.domain.exception.HasErrorCode;

/**
 * A {@code SALE} or {@code WASTE} would take stock below zero: the conditional {@code UPDATE}
 * affected zero rows because the product exists and is managed, but does not hold enough units
 * (inventory.md, section 3.1, section 9).
 */
public final class InventoryInsufficientStockException extends ConflictException implements HasErrorCode {

  /**
   * @param message a message for a person; never exposed raw to the API client
   */
  public InventoryInsufficientStockException(String message) {
    super(message);
  }

  @Override
  public String errorCode() {
    return InventoryErrorCode.INVENTORY_INSUFFICIENT_STOCK.name();
  }
}
