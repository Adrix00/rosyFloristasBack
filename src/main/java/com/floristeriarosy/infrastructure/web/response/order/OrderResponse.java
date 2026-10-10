package com.floristeriarosy.infrastructure.web.response.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * The public view of an order (order.md §6).
 *
 * @param id the order's identifier
 * @param orderNumber the business identifier
 * @param channel the sales channel
 * @param fulfillment how the order reaches the customer
 * @param status the fulfillment status
 * @param items the sold lines
 * @param subtotal the subtotal
 * @param shippingCost the shipping cost
 * @param total the total
 * @param placedAt when the order was placed
 */
public record OrderResponse(
    UUID id,
    String orderNumber,
    String channel,
    String fulfillment,
    String status,
    List<OrderItemResponse> items,
    BigDecimal subtotal,
    BigDecimal shippingCost,
    BigDecimal total,
    Instant placedAt) {

  /** Defensively copies {@code items} (SpotBugs EI_EXPOSE_REP2). */
  public OrderResponse {
    items = items == null ? Collections.emptyList() : List.copyOf(items);
  }

  @Override
  public List<OrderItemResponse> items() {
    return List.copyOf(items);
  }
}
