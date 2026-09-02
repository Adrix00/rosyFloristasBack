package com.floristeriarosy.domain.exception.inventory;

/** Business error codes published by the inventory module (ADR-012; inventory.md, section 9). */
public enum InventoryErrorCode {
  INVENTORY_NOT_MANAGED,
  INVENTORY_INSUFFICIENT_STOCK,
  INVENTORY_ALREADY_INITIALIZED,
  INVENTORY_VALIDATION_FAILED,
  INVENTORY_ALERT_NOT_FOUND,
  INVENTORY_ALERT_NOT_OPEN
}
