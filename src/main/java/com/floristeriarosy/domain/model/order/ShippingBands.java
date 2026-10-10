package com.floristeriarosy.domain.model.order;

import com.floristeriarosy.domain.exception.order.ShippingRatesInvalidException;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The whole set of shipping bands, and the rule that makes them one coherent tariff (order.md,
 * §3.7): among the <em>active</em> bands there is at least one, the first starts at 0 km, and each
 * starts exactly where the previous one ends — no gaps (a gap would refuse an address that is closer
 * than one that is accepted) and no overlaps. A pure function over its input, so it is unit-testable
 * without Spring; the service calls it on the set that would result from every change.
 */
public final class ShippingBands {

  private static final Logger LOGGER = LoggerFactory.getLogger(ShippingBands.class);

  private final List<ShippingBand> all;

  /**
   * @param all every band, active or not, in any order
   */
  public ShippingBands(List<ShippingBand> all) {
    this.all = List.copyOf(all);
  }

  /**
   * @throws ShippingRatesInvalidException the active bands are not one contiguous tariff starting at
   *     0 km; the message says which rule
   */
  public void requireCoherent() {
    List<ShippingBand> active = activeBandsByStart();
    LOGGER.debug("requireCoherent bands={} active={}", all.size(), active.size());
    if (active.isEmpty()) {
      throw new ShippingRatesInvalidException("At least one shipping band must be active");
    }
    if (active.get(0).minKm().signum() != 0) {
      throw new ShippingRatesInvalidException("The first active shipping band must start at 0 km");
    }
    for (int i = 1; i < active.size(); i++) {
      BigDecimal previousEnd = active.get(i - 1).maxKm();
      BigDecimal start = active.get(i).minKm();
      int comparison = start.compareTo(previousEnd);
      if (comparison > 0) {
        throw new ShippingRatesInvalidException(
            "There is a gap between " + previousEnd + " km and " + start + " km");
      }
      if (comparison < 0) {
        throw new ShippingRatesInvalidException(
            "Shipping bands overlap between " + start + " km and " + previousEnd + " km");
      }
    }
    LOGGER.debug("requireCoherent -> coherent");
  }

  /**
   * @return the end of the furthest active band — the delivery radius — or empty if no band is active
   */
  public Optional<BigDecimal> deliveryRadiusKm() {
    return all.stream().filter(ShippingBand::active).map(ShippingBand::maxKm).max(Comparator.naturalOrder());
  }

  /**
   * @return every band, active or not, ordered by where it starts (then by where it ends)
   */
  public List<ShippingBand> orderedByStart() {
    return all.stream()
        .sorted(Comparator.comparing(ShippingBand::minKm).thenComparing(ShippingBand::maxKm))
        .toList();
  }

  /**
   * @return the active bands ordered by where they start
   */
  private List<ShippingBand> activeBandsByStart() {
    return orderedByStart().stream().filter(ShippingBand::active).toList();
  }
}
