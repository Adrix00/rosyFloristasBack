package com.floristeriarosy.application.inventory.port.in;

import com.floristeriarosy.application.inventory.command.ResolveInventoryAlertCommand;
import com.floristeriarosy.application.inventory.dto.InventoryAlertDto;
import com.floristeriarosy.domain.exception.inventory.InventoryAlertNotFoundException;
import com.floristeriarosy.domain.exception.inventory.InventoryAlertNotOpenException;

/** Closes an inventory alert as fixed (inventory.md, section 3.8: "Resolver"). */
public interface ResolveInventoryAlertUseCase {

  /**
   * @param command the alert to close, plus an optional note
   * @return the resolved alert
   * @throws InventoryAlertNotFoundException {@code command.id()} does not exist
   * @throws InventoryAlertNotOpenException the alert is already {@code RESOLVED} or {@code DISMISSED}
   */
  InventoryAlertDto execute(ResolveInventoryAlertCommand command);
}
