package com.floristeriarosy.infrastructure.web.response.order;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One delivery band.
 *
 * @param id the band's identifier
 * @param minKm where the band starts, in kilometres
 * @param maxKm where the band ends (exclusive), in kilometres
 * @param price the shipping cost inside the band
 * @param freeFromAmount the subtotal at or above which shipping is free, or {@code null}
 * @param active whether the band is offered to customers
 */
public record ShippingRateResponse(
    UUID id,
    BigDecimal minKm,
    BigDecimal maxKm,
    BigDecimal price,
    BigDecimal freeFromAmount,
    boolean active) {}
