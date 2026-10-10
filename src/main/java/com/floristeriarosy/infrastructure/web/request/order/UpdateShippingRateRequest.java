package com.floristeriarosy.infrastructure.web.request.order;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Replaces every editable field of a band ({@code PUT} semantics, order.md §4). Digits match the
 * columns: {@code NUMERIC(5,2)} for kilometres, {@code NUMERIC(10,2)} for money.
 *
 * @param minKm where the band starts, in kilometres
 * @param maxKm where the band ends (exclusive), in kilometres
 * @param price the shipping cost inside the band
 * @param freeFromAmount the subtotal at or above which shipping is free; omit for never
 * @param active whether the band is offered to customers
 */
public record UpdateShippingRateRequest(
    @NotNull @DecimalMin("0.00") @Digits(integer = 3, fraction = 2) BigDecimal minKm,
    @NotNull @DecimalMin("0.00") @Digits(integer = 3, fraction = 2) BigDecimal maxKm,
    @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal price,
    @DecimalMin(value = "0.00", inclusive = false) @Digits(integer = 8, fraction = 2)
        BigDecimal freeFromAmount,
    @NotNull Boolean active) {}
