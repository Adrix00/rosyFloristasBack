package com.floristeriarosy.application.inventory.port.in;

import com.floristeriarosy.application.inventory.command.RegisterAdjustmentCommand;
import com.floristeriarosy.application.inventory.dto.StockMovementDto;
import com.floristeriarosy.domain.exception.inventory.InventoryInsufficientStockException;
import com.floristeriarosy.domain.exception.inventory.InventoryNotManagedException;

/** Records a manual stock correction (inventory.md, section 3.6). */
public interface RegisterAdjustmentUseCase {

  /**
   * @param command the product, signed delta and required note
   * @return the recorded {@code ADJUSTMENT} movement
   * @throws InventoryNotManagedException the product has {@code stock = NULL}
   * @throws InventoryInsufficientStockException a negative delta would take stock below zero
   */
  StockMovementDto execute(RegisterAdjustmentCommand command);
}
