package com.floristeriarosy.application.payment.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.floristeriarosy.application.order.port.in.ConfirmCheckoutUseCase;
import com.floristeriarosy.infrastructure.payment.redsys.RedsysProperties;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ReceiveRedsysNotificationServiceTest {

  private static final RedsysProperties PROPERTIES =
      new RedsysProperties(
          "999008881",
          "001",
          "sq7HjrUOBfKmC576ILgskD5srU870gJ7",
          "https://sis-t.redsys.es:25443/sis/realizarPago",
          "https://sis-t.redsys.es:25443/sis/rest/trataPeticionREST",
          "http://localhost:8080/api/v1/payments/redsys/notification",
          "http://localhost:5173/checkout/ok",
          "http://localhost:5173/checkout/ko");

  private final ConfirmCheckoutUseCase confirmCheckoutUseCase = mock(ConfirmCheckoutUseCase.class);
  private final ReceiveRedsysNotificationService service =
      new ReceiveRedsysNotificationService(PROPERTIES, new ObjectMapper(), confirmCheckoutUseCase);

  @Test
  void approvedNotificationConfirmsTheCheckout() {
    String params =
        Base64.getEncoder()
            .encodeToString(
                ("{\"Ds_Merchant_Order\":\"WEB-20260914-000001\","
                    + "\"Ds_Response\":\"0000\","
                    + "\"Ds_AuthorisationCode\":\"123456\","
                    + "\"Ds_Merchant_MerchantData\":\"idem-1\"}")
                    .getBytes());
    String signature =
        com.floristeriarosy.infrastructure.payment.redsys.RedsysSignature.sign(
            PROPERTIES.secretKey(), "WEB-20260914-000001", params);

    service.execute(params, signature);

    verify(confirmCheckoutUseCase).execute(any());
  }

  @Test
  void tamperedSignatureIsRejectedBeforeConfirming() {
    String params =
        Base64.getEncoder()
            .encodeToString(
                "{\"Ds_Merchant_Order\":\"WEB-20260914-000002\",\"Ds_Response\":\"0000\"}"
                    .getBytes());

    org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
        .isThrownBy(() -> service.execute(params, "not-a-real-signature"));
    verify(confirmCheckoutUseCase, never()).execute(any());
  }
}
