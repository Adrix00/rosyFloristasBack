package com.floristeriarosy.infrastructure.geocoding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Straight-line distance (order.md, rule 3.7) — Google gives coordinates, this gives kilometers. */
class HaversineTest {

  @Test
  void distanceBetweenTheSamePointIsZero() {
    BigDecimal distance =
        Haversine.distanceKm(
            new BigDecimal("40.4168"), new BigDecimal("-3.7038"),
            new BigDecimal("40.4168"), new BigDecimal("-3.7038"));

    assertThat(distance).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void distanceBetweenMadridAndBarcelonaIsRoughlyFiveHundredKm() {
    BigDecimal distance =
        Haversine.distanceKm(
            new BigDecimal("40.4168"), new BigDecimal("-3.7038"),
            new BigDecimal("41.3874"), new BigDecimal("2.1686"));

    assertThat(distance.doubleValue()).isCloseTo(505.0, within(15.0));
  }
}
