package com.floristeriarosy.infrastructure.web.request.discount;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * {@code originalPrice} is never accepted from the client: the service derives it from the
 * product's current price at creation time (product-discounts.md, section 5).
 *
 * @param salePrice required; must end up lower than the product's current price
 * @param startsAt required; when the discount becomes active
 * @param endsAt required; when the discount stops being active
 * @param quantityLimit optional; absent means no limit
 */
public record CreateDiscountRequest(
    @NotNull @PositiveOrZero @Digits(integer = 8, fraction = 2) BigDecimal salePrice,
    @NotNull Instant startsAt,
    @NotNull Instant endsAt,
    @Positive Integer quantityLimit) {}
