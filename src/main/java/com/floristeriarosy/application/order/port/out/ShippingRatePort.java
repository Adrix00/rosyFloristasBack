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
   * Like {@link #findAll()}, but first takes the tariff mutex (held until the caller's transaction
   * ends) and reads the bands after it, so two administrators editing the tariff at once are
   * serialized and the second sees everything the first committed — including a band the first
   * inserted, which a row lock would not show it.
   *
   * @return every band, active or not, ordered by where it starts, read under the tariff mutex
   */
  List<ShippingBand> findAllUnderTariffLock();

  /**
   * Must be preceded, in the same transaction, by {@link #findAllUnderTariffLock()}: that is what
   * serializes tariff writers, and nothing here takes the lock.
   *
   * @param band the band to insert or update
   * @return the saved band, with timestamps populated
   * @throws ShippingRatesInvalidException the database refused the change (overlapping active
   *     bands); the service checks coherence first, this is the backstop
   */
  ShippingBand save(ShippingBand band);
}
