package com.floristeriarosy.application.cart.dto;

import com.floristeriarosy.domain.model.product.ProductStatus;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A product's display-ready pricing and status snapshot, batch-loaded for every line of a cart in
 * one query (cart.md §8, {@code CartPricingPort}). Not a cart line itself: quantity comes from the
 * domain {@code Cart} aggregate, never stored here.
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
 * @param status the product's status (product.md §3.2)
 * @param availableQuantity the current stock, or {@code null} if inventory is unmanaged
 */
public record CartCatalogEntryDto(
    UUID productId,
    String productName,
    String productSlug,
    String mainImageUrl,
    BigDecimal unitPrice,
    BigDecimal effectivePrice,
    boolean onSale,
    Map<String, Object> productAttributes,
    UUID discountId,
    ProductStatus status,
    Integer availableQuantity) {

  /**
   * Defensively copies {@code productAttributes} (SpotBugs EI_EXPOSE_REP2), using a plain {@link
   * LinkedHashMap} rather than {@link Map#copyOf} so a {@code null} value is tolerated.
   */
  public CartCatalogEntryDto {
    productAttributes = Collections.unmodifiableMap(new LinkedHashMap<>(productAttributes));
  }
}
