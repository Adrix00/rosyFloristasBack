package com.floristeriarosy.application.discount.dto;

import com.floristeriarosy.domain.model.discount.DiscountState;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Read shape of a discount returned by every use case in this module. Kept outside {@code domain}
 * so Controllers never touch a domain type directly (HexagonalArchitectureTest).
 *
 * @param id the identifier
 * @param productId the product this discount applies to
 * @param originalPrice the product's price at the moment of creation, frozen
 * @param salePrice the promotional price
 * @param startsAt when this discount becomes active
 * @param endsAt when this discount stops being active
 * @param quantityLimit the maximum number of promotional units, or {@code null} for no limit
 * @param quantitySold units already sold under this discount
 * @param state the derived lifecycle state (product-discounts.md, section 6)
 * @param createdAt when the row was created
 * @param updatedAt when the row was last updated
 */
public record DiscountDto(
    UUID id,
    UUID productId,
    BigDecimal originalPrice,
    BigDecimal salePrice,
    Instant startsAt,
    Instant endsAt,
    Integer quantityLimit,
    Integer quantitySold,
    DiscountState state,
    Instant createdAt,
    Instant updatedAt) {}
