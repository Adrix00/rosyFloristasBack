package com.floristeriarosy.infrastructure.web.response.product;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * @param id the identifier
 * @param name the product name
 * @param slug the product slug
 * @param price the base price
 * @param effectivePrice the price with an active discount applied, if any; otherwise equal to
 *     {@code price}
 * @param onSale whether {@code effectivePrice} differs from {@code price}
 * @param mainImageUrl the public CDN URL of the position-0 image, or {@code null} — always {@code
 *     null} until image.md's builder exists (tracked gap, same as category's {@code imageUrl})
 */
public record ProductSummaryResponse(
    UUID id,
    String name,
    String slug,
    BigDecimal price,
    BigDecimal effectivePrice,
    boolean onSale,
    String mainImageUrl) {}
