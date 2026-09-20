package com.floristeriarosy.application.order.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * The whole tariff for the admin panel.
 *
 * @param bands every band, active or not, ordered by where it starts
 * @param deliveryRadiusKm the current delivery radius — the end of the furthest active band — or
 *     {@code null} if no band is active
 */
public record ShippingBandsDto(List<ShippingBandDto> bands, BigDecimal deliveryRadiusKm) {

  /** Defensively copies {@code bands} (SpotBugs EI_EXPOSE_REP2). */
  public ShippingBandsDto {
    bands = List.copyOf(bands);
  }
}
