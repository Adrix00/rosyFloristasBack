package com.floristeriarosy.application.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.payment.port.out.PaymentWritePort;
import com.floristeriarosy.domain.model.order.valueobject.OrderId;
import com.floristeriarosy.domain.model.payment.Payment;
import com.floristeriarosy.domain.model.payment.PaymentMethod;
import com.floristeriarosy.domain.model.payment.PaymentStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** {@link ConfirmOrderPaymentService} records a Redsys-approved charge as a captured payment. */
class ConfirmOrderPaymentServiceTest {

  private final PaymentWritePort writePort = mock(PaymentWritePort.class);
  private final ConfirmOrderPaymentService service = new ConfirmOrderPaymentService(writePort);

  @Test
  void confirmChargeSavesAnAlreadyCapturedPayment() {
    when(writePort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    Payment result =
        service.execute(OrderId.newId(), PaymentMethod.CARD_ONLINE, new BigDecimal("42.00"), "redsys-auth-1");

    assertThat(result.status()).isEqualTo(PaymentStatus.CAPTURED);
    assertThat(result.providerPaymentIntentId()).isEqualTo("redsys-auth-1");
  }
}
