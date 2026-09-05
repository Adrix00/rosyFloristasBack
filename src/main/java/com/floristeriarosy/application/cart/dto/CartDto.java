package com.floristeriarosy.application.cart.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * A fully priced cart (cart.md §6, {@code CartResponse}), returned by every use case in this
 * module — the six that write return the resulting cart, not just the touched line.
 *
 * @param id the cart's identifier, or {@code null} if no cart has ever been created for this
 *     visitor (cart.md, rule 3.1)
 * @param customerId the owning customer, or {@code null} for a guest cart
 * @param sessionToken the token identifying this cart while there is no session; not part of the
 *     public {@code CartResponse} body, read only by the controller to set the cookie
 * @param items the priced lines
 * @param subtotal the sum of every line's {@code lineTotal}
 * @param itemCount the sum of every line's quantity
 */
public record CartDto(
    UUID id,
    UUID customerId,
    String sessionToken,
    List<CartItemDto> items,
    BigDecimal subtotal,
    int itemCount) {

  /** Defensively copies {@code items} (SpotBugs EI_EXPOSE_REP2). */
  public CartDto {
    items = List.copyOf(items);
  }

  /**
   * @return the empty, virtual cart returned when no row exists yet (cart.md, rule 3.1: never
   *     created just by visiting)
   */
  public static CartDto empty() {
    return new CartDto(null, null, null, List.of(), BigDecimal.ZERO, 0);
  }
}
