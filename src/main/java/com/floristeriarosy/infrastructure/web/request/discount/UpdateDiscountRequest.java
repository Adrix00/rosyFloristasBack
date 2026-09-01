package com.floristeriarosy.infrastructure.web.request.discount;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Every field is optional at this layer: which ones may actually change depends on the discount's
 * current state (product-discounts.md, section 3.3), validated in the domain layer. An absent
 * field leaves it unchanged; a present field with a value different from the current one, in a
 * state that forbids editing it, is rejected with 422 rather than silently ignored.
 *
 * @param startsAt the requested new vigency start, or {@code null} to leave it unchanged
 * @param endsAt the requested new vigency end, or {@code null} to leave it unchanged
 * @param quantityLimit the requested new unit cap, or {@code null} to leave it unchanged
 * @param salePrice the requested new promotional price, or {@code null} to leave it unchanged
 */
public record UpdateDiscountRequest(
    Instant startsAt,
    Instant endsAt,
    @Positive Integer quantityLimit,
    @PositiveOrZero @Digits(integer = 8, fraction = 2) BigDecimal salePrice) {}
