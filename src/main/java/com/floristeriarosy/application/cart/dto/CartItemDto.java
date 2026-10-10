package com.floristeriarosy.application.cart.dto;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One priced line of a cart (cart.md §6, {@code CartItemResponse}).
 *
 * @param productId the product's identifier
 * @param productName the product's name
 * @param productSlug the product's slug
 * @param mainImageUrl the product's main image URL, or {@code null}
 * @param unitPrice the product's base price
 * @param effectivePrice the current price, with an active discount applied if any
 * @param onSale whether {@code effectivePrice} reflects an active discount
 * @param productAttributes the product's attribute values at the moment of pricing
 * @param discountId the active promotion's identifier, or {@code null}
 * @param quantity the quantity in the cart
 * @param lineTotal {@code effectivePrice * quantity}
 * @param availableQuantity the current stock, or {@code null} if inventory is unmanaged
 */
public record CartItemDto(
    UUID productId,
    String productName,
    String productSlug,
    String mainImageUrl,
    BigDecimal unitPrice,
    BigDecimal effectivePrice,
    boolean onSale,
    Map<String, Object> productAttributes,
    UUID discountId,
    int quantity,
    BigDecimal lineTotal,
    Integer availableQuantity) {

  /**
   * Defensively copies {@code productAttributes} (SpotBugs EI_EXPOSE_REP2), using a plain {@link
   * LinkedHashMap} rather than {@link Map#copyOf} so a {@code null} value is tolerated.
   */
  public CartItemDto {
    productAttributes = Collections.unmodifiableMap(new LinkedHashMap<>(productAttributes));
  }
}
