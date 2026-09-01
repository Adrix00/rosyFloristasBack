package com.floristeriarosy.application.discount.command;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * @param productId the product this discount applies to
 * @param salePrice the promotional price
 * @param startsAt when the discount becomes active
 * @param endsAt when the discount stops being active
 * @param quantityLimit the maximum number of promotional units, or {@code null} for no limit
 */
public record CreateDiscountCommand(
    UUID productId, BigDecimal salePrice, Instant startsAt, Instant endsAt, Integer quantityLimit) {}
