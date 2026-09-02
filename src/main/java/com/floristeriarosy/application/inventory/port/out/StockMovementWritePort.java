package com.floristeriarosy.application.inventory.port.out;

import com.floristeriarosy.domain.exception.inventory.InventoryAlreadyInitializedException;
import com.floristeriarosy.domain.model.inventory.StockMovement;

/** Persists a stock movement (ADR-003; inventory.md, section 8). Insert-only: this table never updates. */
public interface StockMovementWritePort {

  /**
   * @param movement the movement to insert
   * @return the saved movement, with {@code createdAt} populated by the database
   * @throws InventoryAlreadyInitializedException {@code ux_stock_movements_initial} was violated —
   *     a second {@code INITIAL} for the same product
   */
  StockMovement save(StockMovement movement);
}
