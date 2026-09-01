package com.floristeriarosy.infrastructure.web.response.discount;

import com.floristeriarosy.domain.model.discount.DiscountState;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * @param id the identifier
 * @param productId the product this discount applies to
 * @param originalPrice the product's price at the moment of creation, frozen
 * @param salePrice the promotional price
 * @param startsAt when this discount becomes active
 * @param endsAt when this discount stops being active
 * @param quantityLimit the maximum number of promotional units, or {@code null} for no limit
 * @param quantitySold units already sold under this discount
 * @param state {@code SCHEDULED}, {@code ACTIVE}, {@code SOLD_OUT} or {@code ENDED}, derived
 *     (product-discounts.md, section 6)
 * @param createdAt when the row was created
 * @param updatedAt when the row was last updated
 */
public record DiscountResponse(
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
