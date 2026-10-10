package com.floristeriarosy.application.order.dto;

import java.math.BigDecimal;

/**
 * @param price the shipping cost for this distance band
 * @param freeFromAmount the subtotal at or above which shipping is free, or {@code null}
 */
public record ShippingRate(BigDecimal price, BigDecimal freeFromAmount) {}
