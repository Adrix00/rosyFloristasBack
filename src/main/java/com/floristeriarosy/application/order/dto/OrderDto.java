package com.floristeriarosy.application.order.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The public view of an order (order.md §6) — never the decrypted {@code buyer_*} beyond what the
 * owner already knows, never {@code customer_id}.
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
public record OrderDto(
    UUID id,
    String orderNumber,
    String channel,
    String fulfillment,
    String status,
    List<Item> items,
    BigDecimal subtotal,
    BigDecimal shippingCost,
    BigDecimal total,
    Instant placedAt) {

  /** Defensively copies {@code items} (SpotBugs EI_EXPOSE_REP2). */
  public OrderDto {
    items = items == null ? Collections.emptyList() : List.copyOf(items);
  }

  @Override
  public List<Item> items() {
    return List.copyOf(items);
  }

  /**
   * @param productId the sold product
   * @param productName the snapshot name
   * @param productAttributes the snapshot attributes
   * @param unitPrice the snapshot unit price
   * @param discountPrice the snapshot discount price, or {@code null}
   * @param quantity units sold
   * @param lineTotal the line's total
   */
  public record Item(
      UUID productId,
      String productName,
      Map<String, Object> productAttributes,
      BigDecimal unitPrice,
      BigDecimal discountPrice,
      int quantity,
      BigDecimal lineTotal) {

    /** Defensively copies {@code productAttributes} (SpotBugs EI_EXPOSE_REP2). */
    public Item {
      productAttributes =
          productAttributes == null
              ? Collections.emptyMap()
              : Collections.unmodifiableMap(new LinkedHashMap<>(productAttributes));
    }

    @Override
    public Map<String, Object> productAttributes() {
      return Map.copyOf(productAttributes);
    }
  }
}
