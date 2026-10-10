package com.floristeriarosy.infrastructure.payment.redsys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;
import org.junit.jupiter.api.Test;

/** {@link RedsysOrderId} maps the business order number to Redsys's 12-digit order format. */
class RedsysOrderIdTest {

  @Test
  void padsTheSequencePartToTwelveDigits() {
    assertThat(RedsysOrderId.from(OrderNumber.of("WEB-20260826-000001")))
        .isEqualTo("000000000001");
  }

  @Test
  void ignoresTheChannelAndDatePrefix() {
    assertThat(RedsysOrderId.from(OrderNumber.of("STORE-20260826-000042")))
        .isEqualTo("000000000042");
  }

  @Test
  void rejectsAnOrderNumberWithoutADash() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> RedsysOrderId.from(OrderNumber.of("000001")));
  }

  @Test
  void rejectsAnOrderNumberWithANonNumericSequence() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> RedsysOrderId.from(OrderNumber.of("WEB-20260826-ABCDEF")));
  }
}
