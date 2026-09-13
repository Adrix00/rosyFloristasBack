package com.floristeriarosy.infrastructure.persistence.adapter.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.order.dto.ChargeResult;
import com.floristeriarosy.application.payment.port.in.ChargeOrderPaymentUseCase;
import com.floristeriarosy.application.payment.port.in.RefundOrderPaymentUseCase;
import com.floristeriarosy.domain.model.order.valueobject.OrderId;
import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;
import com.floristeriarosy.domain.model.payment.Payment;
import com.floristeriarosy.domain.model.payment.PaymentMethod;
import com.floristeriarosy.domain.model.payment.PaymentProvider;
import com.floristeriarosy.domain.model.payment.valueobject.PaymentId;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** {@link OrderPaymentAdapter} translates order's port into payment's use cases, nothing else. */
class OrderPaymentAdapterTest {

  private final ChargeOrderPaymentUseCase chargeUseCase = mock(ChargeOrderPaymentUseCase.class);
  private final RefundOrderPaymentUseCase refundUseCase = mock(RefundOrderPaymentUseCase.class);
  private final OrderPaymentAdapter adapter = new OrderPaymentAdapter(chargeUseCase, refundUseCase);

  @Test
  void chargeDelegatesToChargeOrderPaymentUseCase() {
    OrderId orderId = OrderId.newId();
    OrderNumber orderNumber = OrderNumber.of("WEB-20260913-000001");
    Payment captured =
        Payment.initiate(PaymentId.newId(), orderId, PaymentMethod.CASH, PaymentProvider.MANUAL, new BigDecimal("10.00"));
    captured.capture(null, Instant.now());
    when(chargeUseCase.execute(any(), any(), any(), any(), any(), any()))
        .thenReturn(new ChargeResult.Captured(captured));

    ChargeResult result =
        adapter.charge(orderId, orderNumber, new BigDecimal("10.00"), PaymentMethod.CASH, null, "idem-1");

    assertThat(result).isInstanceOf(ChargeResult.Captured.class);
  }

  @Test
  void refundDelegatesToRefundOrderPaymentUseCase() {
    OrderId orderId = OrderId.newId();
    OrderNumber orderNumber = OrderNumber.of("WEB-20260913-000002");
    Payment refunded =
        Payment.initiate(PaymentId.newId(), orderId, PaymentMethod.CASH, PaymentProvider.MANUAL, new BigDecimal("10.00"));
    refunded.capture(null, Instant.now());
    refunded.refundInFull(null, Instant.now());
    when(refundUseCase.execute(any(), any())).thenReturn(refunded);

    Payment result = adapter.refund(orderId, orderNumber);

    assertThat(result.refundedAmount()).isEqualByComparingTo("10.00");
  }
}
