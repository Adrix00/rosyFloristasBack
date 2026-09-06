package com.floristeriarosy.application.cart.port.out;

import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import java.util.Optional;

/**
 * Single-product checks {@code cart} needs at write time (ADR-003; not in cart.md's original §8
 * table — added because rules 3.3 and 3.6 need it, same spirit as {@code ProductInventoryPort}).
 */
public interface CartProductAvailabilityPort {

  /**
   * @param id the product to check
   * @return whether it is {@code ACTIVE} with at least one {@code ACTIVE} category (product.md
   *     §3.3) — the add-time gate of cart.md, rule 3.6
   */
  boolean isVisible(ProductId id);

  /**
   * @param id the product to check
   * @return the current managed stock, or empty if inventory is unmanaged for this product
   *     (cart.md, rule 3.3)
   */
  Optional<Integer> availableStock(ProductId id);
}
