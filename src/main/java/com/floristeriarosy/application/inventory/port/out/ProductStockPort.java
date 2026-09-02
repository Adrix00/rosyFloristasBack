package com.floristeriarosy.application.inventory.port.out;

import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import java.util.Optional;

/**
 * Applies the conditional {@code UPDATE} on {@code products.stock} (ADR-003; ADR-009; inventory.md,
 * section 3.1, section 8). The counterpart of {@code ProductInventoryPort} (product.md, section 8):
 * that one is how {@code product} asks {@code inventory} to initialize or deactivate stock; this one
 * is how {@code inventory} actually writes the row.
 */
public interface ProductStockPort {

  /**
   * {@code UPDATE products SET stock = stock - :quantity WHERE id = :productId AND stock IS NOT
   * NULL AND stock >= :quantity RETURNING stock} — never a {@code SELECT} first (inventory.md,
   * section 3.1).
   *
   * @param productId the product to decrement
   * @param quantity the positive amount to subtract
   * @return the resulting stock, if the row was affected; empty if zero rows matched — the caller
   *     disambiguates {@code stock IS NULL} from insufficient stock with a diagnostic read
   */
  Optional<Integer> decrementConditional(ProductId productId, int quantity);

  /**
   * {@code UPDATE products SET stock = stock + :quantity WHERE id = :productId AND stock IS NOT
   * NULL RETURNING stock} — unconditional beyond being managed, there is no upper bound to check.
   *
   * @param productId the product to increment
   * @param quantity the positive amount to add
   * @return the resulting stock, if the row was affected; empty if the product is unmanaged
   */
  Optional<Integer> incrementConditional(ProductId productId, int quantity);

  /**
   * Sets {@code products.stock} to its starting value, for an {@code INITIAL} movement
   * (inventory.md, section 3.2). Unconditional: the guarantee that this runs at most once per
   * product's history lives on {@code stock_movements} ({@code ux_stock_movements_initial}), not
   * here — the caller is expected to already know {@code productId} exists.
   *
   * @param productId the product to set the starting stock for
   * @param quantity the starting stock
   * @return the resulting stock, equal to {@code quantity}
   */
  int setInitial(ProductId productId, int quantity);

  /**
   * Sets {@code products.stock} back to {@code NULL}: deactivates managed inventory, movement
   * history left intact (product.md, section 3.7).
   *
   * @param productId the product to deactivate
   */
  void clear(ProductId productId);
}
