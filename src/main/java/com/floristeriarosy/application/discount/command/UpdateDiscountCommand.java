package com.floristeriarosy.application.discount.command;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * @param id the discount to update
 * @param startsAt the requested new vigency start, or {@code null} to leave it unchanged
 * @param endsAt the requested new vigency end, or {@code null} to leave it unchanged
 * @param quantityLimit the requested new unit cap, or {@code null} to leave it unchanged
 * @param salePrice the requested new promotional price, or {@code null} to leave it unchanged
 */
public record UpdateDiscountCommand(
    UUID id, Instant startsAt, Instant endsAt, Integer quantityLimit, BigDecimal salePrice) {}
