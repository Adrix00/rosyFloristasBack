package com.floristeriarosy.application.cart.port.out;

import com.floristeriarosy.domain.model.cart.Cart;
import java.util.Optional;
import java.util.UUID;

/**
 * Read capability for cart (ADR-003). Returns the domain aggregate — id, customer, session token,
 * expiry and unpriced lines — never a price (cart.md, rule 3.5): the priced view is a distinct
 * capability, {@link CartPricingPort}.
 */
public interface CartReadPort {

  /**
   * @param customerId the owning customer
   * @return the customer's cart, if one exists
   */
  Optional<Cart> findByCustomer(UUID customerId);

  /**
   * @param sessionToken the cart's session token cookie value
   * @return the matching cart, if one exists
   */
  Optional<Cart> findBySessionToken(String sessionToken);
}
