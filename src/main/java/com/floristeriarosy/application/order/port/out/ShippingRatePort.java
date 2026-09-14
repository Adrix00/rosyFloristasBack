package com.floristeriarosy.application.order.port.out;

import java.math.BigDecimal;
import java.util.Optional;

/** Reads {@code shipping_rates} (order.md, rule 3.7). */
public interface ShippingRatePort {

  /**
   * @param distanceKm the delivery address's distance from the store
   * @return the applicable rate, or empty if the distance falls outside every active band
   */
  Optional<ShippingRate> findRateForDistance(BigDecimal distanceKm);
}
