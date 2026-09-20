package com.floristeriarosy.infrastructure.web.response.order;

import java.math.BigDecimal;
import java.util.List;

/**
 * The whole delivery tariff.
 *
 * @param rates every band, active or not, ordered by where it starts
 * @param deliveryRadiusKm the current delivery radius — the end of the furthest active band — or
 *     {@code null} if no band is active
 */
public record ShippingRatesResponse(List<ShippingRateResponse> rates, BigDecimal deliveryRadiusKm) {}
