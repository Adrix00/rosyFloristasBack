package com.floristeriarosy.domain.model.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.floristeriarosy.domain.exception.order.ShippingRatesInvalidException;
import com.floristeriarosy.domain.model.order.valueobject.ShippingBandId;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link ShippingBands} — the coherence rule of order.md §3.7 over the active bands. */
class ShippingBandsTest {

  @Test
  void theSeedTariffIsCoherent() {
    ShippingBands bands = new ShippingBands(List.of(band(0, 1, true), band(1, 5, true), band(5, 10, true)));

    bands.requireCoherent();

    assertThat(bands.deliveryRadiusKm()).contains(new BigDecimal("10"));
  }

  @Test
  void aSingleActiveBandStartingAtZeroIsCoherent() {
    new ShippingBands(List.of(band(0, 7, true))).requireCoherent();
  }

  @Test
  void theOrderOfTheInputDoesNotMatter() {
    new ShippingBands(List.of(band(5, 10, true), band(0, 1, true), band(1, 5, true))).requireCoherent();
  }

  @Test
  void inactiveBandsDoNotCount() {
    new ShippingBands(List.of(band(0, 5, true), band(5, 10, false), band(20, 30, false))).requireCoherent();
  }

  @Test
  void withoutAnActiveBandNobodyGetsDelivery() {
    assertThatThrownBy(() -> new ShippingBands(List.of(band(0, 5, false))).requireCoherent())
        .isInstanceOf(ShippingRatesInvalidException.class)
        .hasMessageContaining("At least one");
    assertThatThrownBy(() -> new ShippingBands(List.of()).requireCoherent())
        .isInstanceOf(ShippingRatesInvalidException.class);
  }

  @Test
  void theFirstActiveBandMustStartAtZero() {
    assertThatThrownBy(() -> new ShippingBands(List.of(band(1, 5, true), band(5, 10, true))).requireCoherent())
        .isInstanceOf(ShippingRatesInvalidException.class)
        .hasMessageContaining("start at 0 km");
  }

  @Test
  void aGapBetweenActiveBandsIsRefused() {
    assertThatThrownBy(() -> new ShippingBands(List.of(band(0, 1, true), band(2, 5, true))).requireCoherent())
        .isInstanceOf(ShippingRatesInvalidException.class)
        .hasMessageContaining("gap between 1 km and 2 km");
  }

  @Test
  void anOverlapBetweenActiveBandsIsRefused() {
    assertThatThrownBy(() -> new ShippingBands(List.of(band(0, 5, true), band(3, 8, true))).requireCoherent())
        .isInstanceOf(ShippingRatesInvalidException.class)
        .hasMessageContaining("overlap");
  }

  @Test
  void deactivatingAMiddleBandLeavesAGap() {
    assertThatThrownBy(
            () -> new ShippingBands(List.of(band(0, 1, true), band(1, 5, false), band(5, 10, true))).requireCoherent())
        .isInstanceOf(ShippingRatesInvalidException.class)
        .hasMessageContaining("gap");
  }

  @Test
  void theRadiusIsTheEndOfTheFurthestActiveBandAndEmptyWhenNoneIsActive() {
    assertThat(new ShippingBands(List.of(band(0, 5, true), band(5, 15, true), band(15, 20, false))).deliveryRadiusKm())
        .contains(new BigDecimal("15"));
    assertThat(new ShippingBands(List.of(band(0, 5, false))).deliveryRadiusKm()).isEmpty();
  }

  @Test
  void bandsAreListedByWhereTheyStart() {
    ShippingBands bands = new ShippingBands(List.of(band(5, 10, true), band(0, 1, false), band(1, 5, true)));

    assertThat(bands.orderedByStart()).extracting(b -> b.minKm().intValue()).containsExactly(0, 1, 5);
  }

  private ShippingBand band(int minKm, int maxKm, boolean active) {
    return ShippingBand.create(
        ShippingBandId.newId(), new BigDecimal(minKm), new BigDecimal(maxKm), new BigDecimal("5.00"), null, active);
  }
}
