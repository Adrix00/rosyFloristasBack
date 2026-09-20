package com.floristeriarosy.domain.model.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.floristeriarosy.domain.exception.order.ShippingRatesInvalidException;
import com.floristeriarosy.domain.model.order.valueobject.ShippingBandId;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link ShippingBand} — the per-band invariants and the change list for the audit log. */
class ShippingBandTest {

  private static final BigDecimal ZERO = BigDecimal.ZERO;

  @Test
  void aValidBandIsCreated() {
    ShippingBand band = create("0", "5", "8.00", "70.00", true);

    assertThat(band.maxKm()).isEqualByComparingTo("5");
    assertThat(band.freeFromAmount()).isEqualByComparingTo("70.00");
    assertThat(band.active()).isTrue();
  }

  @Test
  void aBandWithoutAFreeShippingThresholdIsValid() {
    assertThat(create("0", "5", "8.00", null, true).freeFromAmount()).isNull();
  }

  @Test
  void aFreePriceIsValid() {
    assertThat(create("0", "5", "0.00", null, true).price()).isEqualByComparingTo(ZERO);
  }

  @Test
  void theInvariantsAreEnforced() {
    assertThatThrownBy(() -> create("-1", "5", "8", null, true)).isInstanceOf(ShippingRatesInvalidException.class);
    assertThatThrownBy(() -> create("5", "5", "8", null, true)).isInstanceOf(ShippingRatesInvalidException.class);
    assertThatThrownBy(() -> create("5", "3", "8", null, true)).isInstanceOf(ShippingRatesInvalidException.class);
    assertThatThrownBy(() -> create("0", "5", "-0.01", null, true)).isInstanceOf(ShippingRatesInvalidException.class);
    assertThatThrownBy(() -> create("0", "5", "8", "0", true)).isInstanceOf(ShippingRatesInvalidException.class);
    assertThatThrownBy(() -> create("0", "5", "8", "-1", true)).isInstanceOf(ShippingRatesInvalidException.class);
    assertThatThrownBy(() -> ShippingBand.create(ShippingBandId.newId(), null, BigDecimal.ONE, BigDecimal.ONE, null, true))
        .isInstanceOf(ShippingRatesInvalidException.class);
  }

  @Test
  void replaceWithReportsExactlyTheColumnsThatChanged() {
    ShippingBand band = create("0", "5", "8.00", "70.00", true);

    List<String> changed =
        band.replaceWith(new BigDecimal("0"), new BigDecimal("6"), new BigDecimal("9.00"), null, false);

    assertThat(changed).containsExactly("max_km", "price", "free_from_amount", "active");
    assertThat(band.maxKm()).isEqualByComparingTo("6");
    assertThat(band.freeFromAmount()).isNull();
    assertThat(band.active()).isFalse();
  }

  @Test
  void replaceWithTheSameValuesInAnotherScaleChangesNothing() {
    ShippingBand band = create("0", "5", "8.00", "70.00", true);

    assertThat(band.replaceWith(new BigDecimal("0.00"), new BigDecimal("5.0"), new BigDecimal("8"), new BigDecimal("70"), true))
        .isEmpty();
  }

  @Test
  void aReplacementThatBreaksAnInvariantChangesNothing() {
    ShippingBand band = create("0", "5", "8.00", "70.00", true);

    assertThatThrownBy(() -> band.replaceWith(new BigDecimal("5"), new BigDecimal("2"), new BigDecimal("9"), null, false))
        .isInstanceOf(ShippingRatesInvalidException.class);

    assertThat(band.minKm()).isEqualByComparingTo("0");
    assertThat(band.price()).isEqualByComparingTo("8.00");
    assertThat(band.active()).isTrue();
  }

  private ShippingBand create(String minKm, String maxKm, String price, String freeFrom, boolean active) {
    return ShippingBand.create(
        ShippingBandId.newId(),
        new BigDecimal(minKm),
        new BigDecimal(maxKm),
        new BigDecimal(price),
        freeFrom == null ? null : new BigDecimal(freeFrom),
        active);
  }
}
