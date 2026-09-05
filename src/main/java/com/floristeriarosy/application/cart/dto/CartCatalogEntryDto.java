package com.floristeriarosy.application.cart.dto;

import com.floristeriarosy.domain.model.product.ProductStatus;
import java.math.BigDecimal;
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
    ProductStatus status,
    Integer availableQuantity) {}
