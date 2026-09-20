package com.floristeriarosy.application.order.port.out;

import com.floristeriarosy.application.order.dto.ShippingRate;
import com.floristeriarosy.domain.exception.order.ShippingRatesInvalidException;
import com.floristeriarosy.domain.model.order.ShippingBand;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** Reads and writes {@code shipping_rates} (order.md, rule 3.7). */
public interface ShippingRatePort {

  /**
   * @param distanceKm the delivery address's distance from the store
   * @return the applicable rate, or empty if the distance falls outside every active band
   */
  Optional<ShippingRate> findRateForDistance(BigDecimal distanceKm);

  /**
   * @return the delivery radius — the end of the furthest active band — or empty if no band is
   *     active
   */
  Optional<BigDecimal> findMaxDeliveryDistanceKm();

  /**
   * @return every band, active or not, ordered by where it starts
   */
  List<ShippingBand> findAll();

  /**
   * Like {@link #findAll()}, but locks every row until the caller's transaction ends, so two
   * administrators editing the tariff at once are serialized and the second sees the first's result.
   *
   * @return every band, active or not, ordered by where it starts, row-locked
   */
  List<ShippingBand> findAllForUpdate();

  /**
   * @param band the band to insert or update
   * @return the saved band, with timestamps populated
   * @throws ShippingRatesInvalidException the database refused the change (overlapping active
   *     bands); the service checks coherence first, this is the backstop
   */
  ShippingBand save(ShippingBand band);
}
