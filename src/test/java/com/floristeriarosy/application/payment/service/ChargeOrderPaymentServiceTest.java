package com.floristeriarosy.application.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.order.dto.ChargeResult;
import com.floristeriarosy.application.payment.dto.GatewayChargeParameters;
import com.floristeriarosy.application.payment.dto.RedsysRedirect;
import com.floristeriarosy.application.payment.port.out.PaymentGatewayPort;
import com.floristeriarosy.domain.model.order.valueobject.OrderId;
import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;
import com.floristeriarosy.domain.model.payment.PaymentMethod;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** {@link ChargeOrderPaymentService} branches by {@link PaymentMethod} (payment.md, rule 3.2). */
class ChargeOrderPaymentServiceTest {

  private final PaymentGatewayPort gatewayPort = mock(PaymentGatewayPort.class);
  private final ChargeOrderPaymentService service = new ChargeOrderPaymentService(gatewayPort);

  @Test
  void cardOnlineDelegatesToTheGatewayAndReturnsARedirect() {
    OrderId orderId = OrderId.newId();
    OrderNumber orderNumber = OrderNumber.of("WEB-20260913-000001");
    RedsysRedirect redirect = new RedsysRedirect("https://sis-t.redsys.es/sis/realizarPago", Map.of("k", "v"));
    when(gatewayPort.charge(new GatewayChargeParameters(orderNumber, new BigDecimal("42.00"), "tok_visa", "idem-1")))
        .thenReturn(redirect);

    ChargeResult result =
        service.execute(
            orderId, orderNumber, new BigDecimal("42.00"), PaymentMethod.CARD_ONLINE, "tok_visa", "idem-1");

    assertThat(result).isInstanceOf(ChargeResult.RedirectRequired.class);
    assertThat(((ChargeResult.RedirectRequired) result).redirectUrl()).isEqualTo(redirect.redirectUrl());
  }

  @Test
  void cashCapturesDirectlyWithoutCallingTheGateway() {
    OrderId orderId = OrderId.newId();
    OrderNumber orderNumber = OrderNumber.of("STORE-20260913-000001");

    ChargeResult result =
        service.execute(orderId, orderNumber, new BigDecimal("15.00"), PaymentMethod.CASH, null, "idem-2");

    assertThat(result).isInstanceOf(ChargeResult.Captured.class);
    assertThat(((ChargeResult.Captured) result).payment().status())
        .isEqualTo(com.floristeriarosy.domain.model.payment.PaymentStatus.CAPTURED);
    org.mockito.Mockito.verifyNoInteractions(gatewayPort);
  }

  @Test
  void interfloraCapturesDirectlyWithoutCallingTheGateway() {
    ChargeResult result =
        service.execute(
            OrderId.newId(), OrderNumber.of("INTERFLORA-20260913-000001"), new BigDecimal("30.00"),
            PaymentMethod.INTERFLORA, null, "idem-3");

    assertThat(result).isInstanceOf(ChargeResult.Captured.class);
  }
}
