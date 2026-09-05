package com.floristeriarosy.application.cart.port.out;

import com.floristeriarosy.domain.model.cart.Cart;
import com.floristeriarosy.domain.model.cart.valueobject.CartId;
import java.time.Instant;

/**
 * Write capability for the {@code carts} row itself (ADR-003). Line-level writes are a distinct
 * capability, {@link CartItemWritePort}, so a single line change never rewrites the whole
 * aggregate.
 */
public interface CartWritePort {

  /**
   * @param cart the cart to insert or update
   * @return the saved cart
   */
  Cart save(Cart cart);

  /**
   * Renews {@code expires_at} without rewriting the whole row (cart.md, rule 3.7).
   *
   * @param id the cart to renew
   * @param expiresAt the new expiry, already computed by the domain
   */
  void touch(CartId id, Instant expiresAt);

  /**
   * Deletes a cart row; {@code cart_items} rows cascade. Used only by the login merge (cart.md,
   * rule 3.2): the guest cart is discarded once its lines have been folded into the customer's.
   *
   * @param id the cart to delete
   */
  void delete(CartId id);
}
