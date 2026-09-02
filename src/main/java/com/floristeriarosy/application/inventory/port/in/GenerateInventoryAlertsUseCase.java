package com.floristeriarosy.application.inventory.port.in;

/**
 * The daily inventory alert job (inventory.md, section 3.8; ADR-013): runs the two detection
 * queries and opens a new alert for every result that does not already have one open.
 */
public interface GenerateInventoryAlertsUseCase {

  /** Runs both detection queries and opens the missing alerts. Never closes an existing one. */
  void execute();
}
