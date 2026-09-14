package com.floristeriarosy.infrastructure.web.response.order;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One public line of an order (order.md §6).
 *
 * @param productId the sold product
 * @param productName the snapshot name
 * @param productAttributes the snapshot attributes
 * @param unitPrice the snapshot unit price
 * @param discountPrice the snapshot discount price, or {@code null}
 * @param quantity units sold
 * @param lineTotal the line's total
 */
public record OrderItemResponse(
    UUID productId,
    String productName,
    Map<String, Object> productAttributes,
    BigDecimal unitPrice,
    BigDecimal discountPrice,
    int quantity,
    BigDecimal lineTotal) {

  /** Defensively copies {@code productAttributes} (SpotBugs EI_EXPOSE_REP2). */
  public OrderItemResponse {
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
