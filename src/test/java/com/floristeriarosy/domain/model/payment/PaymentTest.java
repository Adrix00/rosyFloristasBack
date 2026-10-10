package com.floristeriarosy.domain.model.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.floristeriarosy.domain.model.order.valueobject.OrderId;
import com.floristeriarosy.domain.model.payment.valueobject.PaymentId;
import java.math.BigDecimal;
import java.time.Instant;
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

  @Test
  void captureMovesFromPendingToCapturedAndStoresTheGatewayReference() {
    Payment payment =
        Payment.initiate(
            PaymentId.newId(), OrderId.newId(), PaymentMethod.CARD_ONLINE, PaymentProvider.REDSYS,
            new BigDecimal("42.00"));
    Instant capturedAt = Instant.parse("2026-09-13T10:00:00Z");

    payment.capture("redsys-auth-123456", capturedAt);

    assertThat(payment.status()).isEqualTo(PaymentStatus.CAPTURED);
    assertThat(payment.providerPaymentIntentId()).isEqualTo("redsys-auth-123456");
    assertThat(payment.capturedAt()).isEqualTo(capturedAt);
  }

  @Test
  void captureAcceptsANullReferenceForManualMethods() {
    Payment payment =
        Payment.initiate(
            PaymentId.newId(), OrderId.newId(), PaymentMethod.CASH, PaymentProvider.MANUAL,
            new BigDecimal("15.00"));

    payment.capture(null, Instant.now());

    assertThat(payment.status()).isEqualTo(PaymentStatus.CAPTURED);
    assertThat(payment.providerPaymentIntentId()).isNull();
  }

  @Test
  void captureRejectsAPaymentThatIsNotPending() {
    Payment payment =
        Payment.initiate(
            PaymentId.newId(), OrderId.newId(), PaymentMethod.CARD_ONLINE, PaymentProvider.REDSYS,
            new BigDecimal("42.00"));
    payment.capture(null, Instant.now());

    assertThatIllegalStateException().isThrownBy(() -> payment.capture(null, Instant.now()));
  }

  @Test
  void failMovesFromPendingToFailedAndStoresTheReason() {
    Payment payment =
        Payment.initiate(
            PaymentId.newId(), OrderId.newId(), PaymentMethod.CARD_ONLINE, PaymentProvider.REDSYS,
            new BigDecimal("42.00"));

    payment.fail("Card declined by issuer");

    assertThat(payment.status()).isEqualTo(PaymentStatus.FAILED);
    assertThat(payment.failureReason()).isEqualTo("Card declined by issuer");
  }

  @Test
  void refundInFullMovesFromCapturedToItselfAndRefundsTheWholeAmount() {
    Payment payment =
        Payment.initiate(
            PaymentId.newId(), OrderId.newId(), PaymentMethod.CARD_ONLINE, PaymentProvider.REDSYS,
            new BigDecimal("42.00"));
    payment.capture("redsys-auth-123456", Instant.now());
    Instant refundedAt = Instant.parse("2026-09-14T10:00:00Z");

    payment.refundInFull("redsys-refund-789", refundedAt);

    assertThat(payment.refundedAmount()).isEqualByComparingTo("42.00");
    assertThat(payment.providerReference()).isEqualTo("redsys-refund-789");
    assertThat(payment.refundedAt()).isEqualTo(refundedAt);
  }

  @Test
  void refundInFullRejectsAPaymentThatIsNotCaptured() {
    Payment payment =
        Payment.initiate(
            PaymentId.newId(), OrderId.newId(), PaymentMethod.CASH, PaymentProvider.MANUAL,
            new BigDecimal("15.00"));

    assertThatIllegalStateException()
        .isThrownBy(() -> payment.refundInFull(null, Instant.now()));
  }
}
