package com.floristeriarosy.domain.model.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

/** Domain rules of {@link OrderDelivery} (order.md, rule 3.8). */
class OrderDeliveryTest {

  private static final byte[] ENCRYPTED = "encrypted".getBytes(StandardCharsets.UTF_8);

  @Test
  void forPickupCarriesNoRecipientOrAddress() {
    OrderDelivery delivery =
        OrderDelivery.forPickup(LocalDate.of(2026, 9, 20), LocalTime.of(10, 0), LocalTime.of(11, 0));

    assertThat(delivery.recipientNameEncrypted()).isNull();
    assertThat(delivery.streetEncrypted()).isNull();
    assertThat(delivery.postalCode()).isNull();
    assertThat(delivery.distanceKm()).isNull();
    assertThat(delivery.deliveryDate()).isEqualTo(LocalDate.of(2026, 9, 20));
  }

  @Test
  void forDeliveryCarriesRecipientAndAddress() {
    OrderDelivery delivery =
        OrderDelivery.forDelivery(
            ENCRYPTED,
            ENCRYPTED,
            ENCRYPTED,
            ENCRYPTED,
            null,
            "28034",
            ENCRYPTED,
            ENCRYPTED,
            new BigDecimal("3.50"),
            LocalDate.of(2026, 9, 20),
            LocalTime.of(10, 0),
            LocalTime.of(11, 0),
            null);

    assertThat(delivery.recipientNameEncrypted()).isEqualTo(ENCRYPTED);
    assertThat(delivery.postalCode()).isEqualTo("28034");
    assertThat(delivery.distanceKm()).isEqualByComparingTo("3.50");
  }

  @Test
  void rejectsASlotWhereFromIsNotBeforeTo() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> OrderDelivery.forPickup(LocalDate.of(2026, 9, 20), LocalTime.of(11, 0), LocalTime.of(11, 0)));
  }
}
