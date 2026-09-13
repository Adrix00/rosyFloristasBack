package com.floristeriarosy.domain.model.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.floristeriarosy.domain.model.order.valueobject.OrderId;
import com.floristeriarosy.domain.model.payment.valueobject.PaymentId;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Domain rules of {@link Payment} (payment.md, section 2). */
class PaymentTest {

  @Test
  void initiateIsBornPendingWithNothingCapturedOrRefundedYet() {
    Payment payment =
        Payment.initiate(
            PaymentId.newId(), OrderId.newId(), PaymentMethod.CARD_ONLINE, PaymentProvider.REDSYS,
            new BigDecimal("42.00"));

    assertThat(payment.status()).isEqualTo(PaymentStatus.PENDING);
    assertThat(payment.amount()).isEqualByComparingTo("42.00");
    assertThat(payment.refundedAmount()).isEqualByComparingTo("0");
    assertThat(payment.capturedAt()).isNull();
    assertThat(payment.providerPaymentIntentId()).isNull();
  }

  @Test
  void initiateForManualProviderCarriesNoGatewayReference() {
    Payment payment =
        Payment.initiate(
            PaymentId.newId(), OrderId.newId(), PaymentMethod.CASH, PaymentProvider.MANUAL,
            new BigDecimal("15.00"));

    assertThat(payment.provider()).isEqualTo(PaymentProvider.MANUAL);
    assertThat(payment.providerReference()).isNull();
  }
}
