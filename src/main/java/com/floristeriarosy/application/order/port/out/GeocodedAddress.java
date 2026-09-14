package com.floristeriarosy.application.order.port.out;

import java.math.BigDecimal;

/**
 * @param latitude the geocoded latitude
 * @param longitude the geocoded longitude
 * @param distanceKm the straight-line distance from the store (order.md, rule 3.7)
 */
public record GeocodedAddress(BigDecimal latitude, BigDecimal longitude, BigDecimal distanceKm) {}
