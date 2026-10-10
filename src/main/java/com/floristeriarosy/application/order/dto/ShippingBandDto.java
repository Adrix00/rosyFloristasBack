package com.floristeriarosy.application.order.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One delivery band as the admin panel sees it.
 *
 * @param id the band's identifier
 * @param minKm where the band starts, in kilometres
 * @param maxKm where the band ends (exclusive), in kilometres
 * @param price the shipping cost inside the band
 * @param freeFromAmount the subtotal at or above which shipping is free, or {@code null}
 * @param active whether the band is offered to customers
 */
public record ShippingBandDto(
    UUID id,
    BigDecimal minKm,
    BigDecimal maxKm,
    BigDecimal price,
    BigDecimal freeFromAmount,
    boolean active) {}
