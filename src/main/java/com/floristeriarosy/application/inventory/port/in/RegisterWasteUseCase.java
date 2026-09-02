package com.floristeriarosy.application.inventory.port.in;

import com.floristeriarosy.application.inventory.command.RegisterWasteCommand;
import com.floristeriarosy.application.inventory.dto.StockMovementDto;
import com.floristeriarosy.domain.exception.inventory.InventoryInsufficientStockException;
import com.floristeriarosy.domain.exception.inventory.InventoryNotManagedException;

/** Records an explicit administrator write-off (inventory.md, section 3.5). */
public interface RegisterWasteUseCase {

  /**
   * @param command the product, wasted quantity (positive) and required note
   * @return the recorded {@code WASTE} movement
   * @throws InventoryNotManagedException the product has {@code stock = NULL}
   * @throws InventoryInsufficientStockException the wasted quantity exceeds available stock
   */
  StockMovementDto execute(RegisterWasteCommand command);
}
