package com.floristeriarosy.infrastructure.web.response.cart;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * @param id the cart's identifier, or {@code null} if none has ever been created (cart.md, rule
 *     3.1)
 * @param items the priced lines
 * @param subtotal the sum of every line's {@code lineTotal}
 * @param itemCount the sum of every line's quantity
 */
public record CartResponse(UUID id, List<CartItemResponse> items, BigDecimal subtotal, int itemCount) {

  /** Defensively copies {@code items} (SpotBugs EI_EXPOSE_REP2). */
  public CartResponse {
    items = List.copyOf(items);
  }
}
