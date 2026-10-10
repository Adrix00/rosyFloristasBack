package com.floristeriarosy.application.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.payment.dto.GatewayRefundParameters;
import com.floristeriarosy.application.payment.dto.GatewayRefundResult;
import com.floristeriarosy.application.payment.port.out.PaymentGatewayPort;
import com.floristeriarosy.application.payment.port.out.PaymentReadPort;
import com.floristeriarosy.application.payment.port.out.PaymentWritePort;
import com.floristeriarosy.domain.exception.payment.PaymentGatewayException;
import com.floristeriarosy.domain.exception.payment.PaymentNotFoundException;
import com.floristeriarosy.domain.model.order.valueobject.OrderId;
import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;
import com.floristeriarosy.domain.model.payment.Payment;
import com.floristeriarosy.domain.model.payment.PaymentMethod;
import com.floristeriarosy.domain.model.payment.PaymentProvider;
import com.floristeriarosy.domain.model.payment.valueobject.PaymentId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** {@link RefundOrderPaymentService} — always a full, automatic refund (payment.md, rule 3.3). */
class RefundOrderPaymentServiceTest {

  private final PaymentReadPort readPort = mock(PaymentReadPort.class);
  private final PaymentWritePort writePort = mock(PaymentWritePort.class);
  private final PaymentGatewayPort gatewayPort = mock(PaymentGatewayPort.class);
  private final RefundOrderPaymentService service = new RefundOrderPaymentService(readPort, writePort, gatewayPort);

  @Test
  void cardOnlineCallsTheGatewayThenSavesTheRefund() {
    OrderId orderId = OrderId.newId();
    OrderNumber orderNumber = OrderNumber.of("WEB-20260913-000001");
    Payment captured =
        Payment.initiate(PaymentId.newId(), orderId, PaymentMethod.CARD_ONLINE, PaymentProvider.REDSYS, new BigDecimal("42.00"));
    captured.capture("redsys-auth-1", Instant.now());
    when(readPort.findByOrderId(orderId)).thenReturn(Optional.of(captured));
    when(gatewayPort.refund(any())).thenReturn(new GatewayRefundResult(true, "redsys-refund-1", null));
    when(writePort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    Payment result = service.execute(orderId, orderNumber);

    assertThat(result.refundedAmount()).isEqualByComparingTo("42.00");
    assertThat(result.providerReference()).isEqualTo("redsys-refund-1");
  }

  @Test
  void aGatewayFailureRaisesPaymentGatewayExceptionAndSavesNothing() {
    OrderId orderId = OrderId.newId();
    Payment captured =
        Payment.initiate(PaymentId.newId(), orderId, PaymentMethod.CARD_ONLINE, PaymentProvider.REDSYS, new BigDecimal("42.00"));
    captured.capture("redsys-auth-1", Instant.now());
    when(readPort.findByOrderId(orderId)).thenReturn(Optional.of(captured));
    when(gatewayPort.refund(any())).thenReturn(new GatewayRefundResult(false, null, "SIS0000"));

    assertThatExceptionOfType(PaymentGatewayException.class)
        .isThrownBy(() -> service.execute(orderId, OrderNumber.of("WEB-20260913-000001")))
        .withMessageNotContaining("SIS0000");
    verify(writePort, never()).save(any());
  }

  @Test
  void cashSettlesDirectlyWithoutCallingTheGateway() {
    OrderId orderId = OrderId.newId();
    OrderNumber orderNumber = OrderNumber.of("STORE-20260913-000001");
    Payment captured =
        Payment.initiate(PaymentId.newId(), orderId, PaymentMethod.CASH, PaymentProvider.MANUAL, new BigDecimal("15.00"));
    captured.capture(null, Instant.now());
    when(readPort.findByOrderId(orderId)).thenReturn(Optional.of(captured));
    when(writePort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    Payment result = service.execute(orderId, orderNumber);

    assertThat(result.refundedAmount()).isEqualByComparingTo("15.00");
    verifyNoInteractions(gatewayPort);
  }

  @Test
  void throwsWhenTheOrderHasNoCapturedPayment() {
    OrderId orderId = OrderId.newId();
    when(readPort.findByOrderId(orderId)).thenReturn(Optional.empty());

    org.assertj.core.api.Assertions.assertThatExceptionOfType(PaymentNotFoundException.class)
        .isThrownBy(
            () -> {
              service.execute(orderId, OrderNumber.of("WEB-20260913-000003"));
            });
  }
}
