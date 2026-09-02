package com.floristeriarosy.domain.model.inventory;

/**
 * The five kinds of stock movement (inventory.md, section 1), each with a mandatory sign enforced
 * both by {@link StockMovement} and by the database ({@code chk_stock_movements_*_sign}).
 */
public enum StockMovementType {
  /** First-time activation of inventory management; {@code quantity >= 0}, once per product. */
  INITIAL,
  /** Merchandise received from a supplier; {@code quantity > 0}. */
  PURCHASE,
  /** A confirmed order; {@code quantity < 0}. */
  SALE,
  /** An explicit administrator write-off; {@code quantity < 0}. */
  WASTE,
  /** A manual correction or reactivation; {@code quantity != 0}, either sign. */
  ADJUSTMENT
}
