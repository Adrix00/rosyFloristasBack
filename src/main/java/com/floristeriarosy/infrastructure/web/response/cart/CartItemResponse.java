package com.floristeriarosy.infrastructure.web.response.cart;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * @param productId the product's identifier
 * @param productName the product's name
 * @param productSlug the product's slug
 * @param mainImageUrl the product's main image URL, or {@code null}
 * @param unitPrice the product's base price
 * @param effectivePrice the current price, with an active discount applied if any
 * @param onSale whether {@code effectivePrice} reflects an active discount
 * @param quantity the quantity in the cart
 * @param lineTotal {@code effectivePrice * quantity}
 * @param availableQuantity the current stock, or {@code null} if inventory is unmanaged
 */
public record CartItemResponse(
    UUID productId,
    String productName,
    String productSlug,
    String mainImageUrl,
    BigDecimal unitPrice,
    BigDecimal effectivePrice,
    boolean onSale,
    int quantity,
    BigDecimal lineTotal,
    Integer availableQuantity) {}
