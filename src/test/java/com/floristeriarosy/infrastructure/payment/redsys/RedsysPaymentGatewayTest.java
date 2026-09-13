package com.floristeriarosy.infrastructure.payment.redsys;

import static org.assertj.core.api.Assertions.assertThat;

import com.floristeriarosy.application.payment.dto.GatewayChargeParameters;
import com.floristeriarosy.application.payment.dto.RedsysRedirect;
import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/** {@link RedsysPaymentGateway#charge} builds a signed redirect with no network call. */
class RedsysPaymentGatewayTest {

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

  private final RedsysPaymentGateway gateway = new RedsysPaymentGateway(PROPERTIES, new ObjectMapper());

  @Test
  void chargeBuildsARedirectWithASignatureThatVerifies() {
    GatewayChargeParameters request =
        new GatewayChargeParameters(
            OrderNumber.of("WEB-20260913-000001"), new BigDecimal("42.00"), "tok_visa", "idem-key-1");

    RedsysRedirect redirect = gateway.charge(request);

    assertThat(redirect.redirectUrl()).isEqualTo(PROPERTIES.redirectUrl());
    assertThat(redirect.formFields()).containsKeys("Ds_SignatureVersion", "Ds_MerchantParameters", "Ds_Signature");
    String params = redirect.formFields().get("Ds_MerchantParameters");
    String signature = redirect.formFields().get("Ds_Signature");
    assertThat(RedsysSignature.verify(PROPERTIES.secretKey(), "WEB-20260913-000001", params, signature)).isTrue();
  }

  @Test
  void chargeEncodesTheAmountInCents() {
    GatewayChargeParameters request =
        new GatewayChargeParameters(
            OrderNumber.of("WEB-20260913-000002"), new BigDecimal("42.50"), "tok_visa", "idem-key-2");

    RedsysRedirect redirect = gateway.charge(request);

    String decodedParams =
        new String(Base64.getDecoder().decode(redirect.formFields().get("Ds_MerchantParameters")));
    assertThat(decodedParams).contains("\"Ds_Merchant_Amount\":\"4250\"");
  }
}
