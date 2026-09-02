package com.floristeriarosy.domain.model.inventory;

/** Lifecycle of an {@code InventoryAlert} (inventory.md, section 3.8; ADR-013). */
public enum InventoryAlertStatus {
  /** Still active; nobody has acted on it. The only state the daily job ever writes. */
  OPEN,
  /** An administrator fixed the underlying problem. Terminal. */
  RESOLVED,
  /** An administrator acknowledged it and decided no action is needed. Terminal. */
  DISMISSED
}
