package com.floristeriarosy.application.cart.support;

import com.floristeriarosy.application.cart.port.out.CartReadPort;
import com.floristeriarosy.domain.model.cart.Cart;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cart identity resolution shared by every use case (cart.md, rule 3.1: a customer is located by
 * {@code customer_id}, a guest by the session token cookie), plus the rule 3.7 treatment of an
 * expired cart as if it did not exist. Kept as one small helper rather than repeating this logic
 * in six services, same justification as {@code shared.util.LogSanitizer}.
 */
public final class CartFinder {

  private static final Logger LOGGER = LoggerFactory.getLogger(CartFinder.class);

  private CartFinder() {}

  /**
   * @param readPort resolves a cart by customer or by session token
   * @param customerId the authenticated customer, or {@code null} for a guest
   * @param sessionToken the guest cart cookie value, ignored when {@code customerId} is present
   * @return the caller's cart, empty if none exists or the one found is expired (rule 3.7)
   */
  public static Optional<Cart> find(CartReadPort readPort, UUID customerId, String sessionToken) {
    LOGGER.debug("find hasCustomer={} hasSessionToken={}", customerId != null, hasToken(sessionToken));
    Optional<Cart> result = notExpired(resolve(readPort, customerId, sessionToken));
    LOGGER.debug("find -> found={}", result.isPresent());
    return result;
  }

  /**
   * @param cart a cart lookup result
   * @return {@code cart}, or empty if it is present but expired (rule 3.7)
   */
  public static Optional<Cart> notExpired(Optional<Cart> cart) {
    return cart.filter(c -> !c.isExpired());
  }

  /**
   * @param readPort resolves a cart by customer or by session token
   * @param customerId the authenticated customer, or {@code null} for a guest
   * @param sessionToken the guest cart cookie value, ignored when {@code customerId} is present
   * @return the raw lookup result, before the expiry filter
   */
  private static Optional<Cart> resolve(CartReadPort readPort, UUID customerId, String sessionToken) {
    if (customerId != null) {
      return readPort.findByCustomer(customerId);
    }
    if (hasToken(sessionToken)) {
      return readPort.findBySessionToken(sessionToken);
    }
    return Optional.empty();
  }

  /**
   * @param sessionToken a candidate session token
   * @return whether it is present and non-blank
   */
  private static boolean hasToken(String sessionToken) {
    return sessionToken != null && !sessionToken.isBlank();
  }
}
