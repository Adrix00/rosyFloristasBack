package com.floristeriarosy.application.order.command;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Replace every editable field of a delivery band.
 *
 * @param id the band to change
 * @param adminUserId the administrator making the change, for the audit log
 * @param minKm where the band starts, in kilometres
 * @param maxKm where the band ends (exclusive), in kilometres
 * @param price the shipping cost inside the band
 * @param freeFromAmount the subtotal at or above which shipping is free, or {@code null}
 * @param active whether the band is offered to customers
 */
public record UpdateShippingRateCommand(
    UUID id,
    UUID adminUserId,
    BigDecimal minKm,
    BigDecimal maxKm,
    BigDecimal price,
    BigDecimal freeFromAmount,
    boolean active) {}
