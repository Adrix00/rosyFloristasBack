package com.floristeriarosy.application.cart.port.out;

import com.floristeriarosy.domain.model.cart.valueobject.CartId;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import java.util.Set;

/**
 * Write capability for individual {@code cart_items} lines (ADR-003), so adding, updating or
 * removing one product never rewrites the whole cart aggregate.
 */
public interface CartItemWritePort {

  /**
   * Upserts a line: inserts it if {@code (cartId, productId)} has no row yet, otherwise updates its
   * quantity — the {@code UNIQUE (cart_id, product_id)} constraint means there is never more than
   * one row per product.
   *
   * @param cartId the owning cart
   * @param productId the product whose line to write
   * @param quantity the quantity to persist
   */
  void save(CartId cartId, ProductId productId, int quantity);

  /**
   * @param cartId the owning cart
   * @param productId the product whose line to delete
   */
  void delete(CartId cartId, ProductId productId);

  /**
   * Deletes every line of a cart ({@code DELETE /cart}: empties, does not delete the cart row).
   *
   * @param cartId the cart to empty
   */
  void deleteAll(CartId cartId);

  /**
   * Deletes a specific subset of lines (cart.md, rule 3.4: automatic removal of no-longer-{@code
   * ACTIVE} products during validation).
   *
   * @param cartId the owning cart
   * @param productIds the products whose lines to delete
   */
  void deleteAll(CartId cartId, Set<ProductId> productIds);
}
