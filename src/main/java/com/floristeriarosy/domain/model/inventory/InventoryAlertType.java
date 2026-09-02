package com.floristeriarosy.domain.model.inventory;

/** The two conditions {@code inventory_alerts} tracks (inventory.md, section 3.8; ADR-013). */
public enum InventoryAlertType {
  /** {@code products.stock <= low_stock_threshold}: a business concern, restock before it sells out. */
  LOW_STOCK,
  /** {@code products.stock} disagrees with the sum of its movements: an integrity concern. */
  RECONCILIATION_MISMATCH
}
