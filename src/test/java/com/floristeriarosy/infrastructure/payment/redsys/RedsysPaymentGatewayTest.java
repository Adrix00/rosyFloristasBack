package com.floristeriarosy.infrastructure.payment.redsys;

import static org.assertj.core.api.Assertions.assertThat;

import com.floristeriarosy.application.payment.dto.GatewayChargeParameters;
import com.floristeriarosy.application.payment.dto.GatewayRefundParameters;
import com.floristeriarosy.application.payment.dto.GatewayRefundResult;
import com.floristeriarosy.application.payment.dto.RedsysRedirect;
import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** {@link RedsysPaymentGateway} — charge is a signed redirect; refund is a real REST call. */
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

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Test
  void chargeBuildsARedirectWithASignatureThatVerifies() {
    RedsysPaymentGateway gateway = new RedsysPaymentGateway(PROPERTIES, OBJECT_MAPPER);
    GatewayChargeParameters request =
        new GatewayChargeParameters(
            OrderNumber.of("WEB-20260913-000001"), new BigDecimal("42.00"), "tok_visa", "idem-key-1");

    RedsysRedirect redirect = gateway.charge(request);

    assertThat(redirect.redirectUrl()).isEqualTo(PROPERTIES.redirectUrl());
    assertThat(redirect.formFields())
        .containsKeys("Ds_SignatureVersion", "Ds_MerchantParameters", "Ds_Signature");
    String params = redirect.formFields().get("Ds_MerchantParameters");
    String signature = redirect.formFields().get("Ds_Signature");
    assertThat(RedsysSignature.verify(PROPERTIES.secretKey(), "000000000001", params, signature))
        .isTrue();
  }

  @Test
  void chargeEncodesTheAmountInCents() {
    RedsysPaymentGateway gateway = new RedsysPaymentGateway(PROPERTIES, OBJECT_MAPPER);
    GatewayChargeParameters request =
        new GatewayChargeParameters(
            OrderNumber.of("WEB-20260913-000002"), new BigDecimal("42.50"), "tok_visa", "idem-key-2");

    RedsysRedirect redirect = gateway.charge(request);

    String decodedParams =
        new String(Base64.getDecoder().decode(redirect.formFields().get("Ds_MerchantParameters")));
    assertThat(decodedParams).contains("\"Ds_Merchant_Amount\":\"4250\"");
  }

  @Test
  void chargeUsesTwelveDigitNumericRedsysOrderId() {
    RedsysPaymentGateway gateway = new RedsysPaymentGateway(PROPERTIES, OBJECT_MAPPER);
    GatewayChargeParameters request =
        new GatewayChargeParameters(
            OrderNumber.of("WEB-20260913-000042"), new BigDecimal("10.00"), "tok_visa", "idem-key-3");

    RedsysRedirect redirect = gateway.charge(request);

    String decodedParams =
        new String(Base64.getDecoder().decode(redirect.formFields().get("Ds_MerchantParameters")));
    assertThat(decodedParams).contains("\"Ds_Merchant_Order\":\"000000000042\"");
  }

  @Test
  void refundReturnsSuccessForApprovedResponse() throws IOException {
    String responseBody =
        refundResponseBody(
            Map.of(
                "Ds_Response", "0000",
                "Ds_AuthorisationCode", "654321",
                "Ds_Order", "000000000001",
                "Ds_Amount", "4200",
                "Ds_Currency", "978"),
            "000000000001");

    try (LocalRedsysServer server = new LocalRedsysServer(200, responseBody)) {
      RedsysPaymentGateway gateway = gatewayFor(server);

      GatewayRefundResult result =
          gateway.refund(
              new GatewayRefundParameters(
                  "WEB-20260913-000001", "redsys-auth-1", new BigDecimal("42.00")));

      assertThat(result.success()).isTrue();
      assertThat(result.providerReference()).isEqualTo("654321");
      assertThat(result.failureReason()).isNull();
    }
  }

  @Test
  void refundReturnsFailureForRejectionBodyWithoutMerchantParameters() throws IOException {
    try (LocalRedsysServer server =
        new LocalRedsysServer(200, "{\"errorCode\":\"SIS0051\"}")) {
      RedsysPaymentGateway gateway = gatewayFor(server);

      GatewayRefundResult result =
          gateway.refund(
              new GatewayRefundParameters(
                  "WEB-20260913-000001", "redsys-auth-1", new BigDecimal("42.00")));

      assertThat(result.success()).isFalse();
      assertThat(result.failureReason()).contains("SIS0051");
    }
  }

  @Test
  void refundReturnsFailureForNon200Status() throws IOException {
    try (LocalRedsysServer server = new LocalRedsysServer(500, "Internal Server Error")) {
      RedsysPaymentGateway gateway = gatewayFor(server);

      GatewayRefundResult result =
          gateway.refund(
              new GatewayRefundParameters(
                  "WEB-20260913-000001", "redsys-auth-1", new BigDecimal("42.00")));

      assertThat(result.success()).isFalse();
      assertThat(result.failureReason()).contains("HTTP 500");
    }
  }

  @Test
  void refundReturnsFailureForMalformedBody() throws IOException {
    try (LocalRedsysServer server = new LocalRedsysServer(200, "not-json")) {
      RedsysPaymentGateway gateway = gatewayFor(server);

      GatewayRefundResult result =
          gateway.refund(
              new GatewayRefundParameters(
                  "WEB-20260913-000001", "redsys-auth-1", new BigDecimal("42.00")));

      assertThat(result.success()).isFalse();
      assertThat(result.failureReason()).contains("Could not parse");
    }
  }

  @Test
  void refundReturnsFailureWhenServerIsUnreachable() {
    RedsysPaymentGateway gateway =
        new RedsysPaymentGateway(
            withRestUrl("http://localhost:1/no-one-listens"),
            OBJECT_MAPPER,
            HttpClient.newBuilder().connectTimeout(Duration.ofMillis(100)).build(),
            Duration.ofSeconds(1));

    GatewayRefundResult result =
        gateway.refund(
            new GatewayRefundParameters(
                "WEB-20260913-000001", "redsys-auth-1", new BigDecimal("42.00")));

    assertThat(result.success()).isFalse();
    assertThat(result.failureReason()).contains("Redsys call failed");
  }

  @Test
  void refundReturnsFailureWhenRedsysDoesNotAnswerInTime() throws IOException {
    try (LocalRedsysServer server = new LocalRedsysServer(200, "{}", 2_000)) {
      RedsysPaymentGateway gateway =
          new RedsysPaymentGateway(
              withRestUrl(server.url()),
              OBJECT_MAPPER,
              HttpClient.newBuilder().build(),
              Duration.ofMillis(300));

      GatewayRefundResult result =
          gateway.refund(
              new GatewayRefundParameters(
                  "WEB-20260913-000001", "redsys-auth-1", new BigDecimal("42.00")));

      assertThat(result.success()).isFalse();
      assertThat(result.failureReason()).contains("Redsys call failed");
    }
  }

  @Test
  void refundReturnsFailureForDeclinedResponseCode() throws IOException {
    String responseBody =
        refundResponseBody(
            Map.of(
                "Ds_Response", "0190",
                "Ds_Order", "000000000001",
                "Ds_Amount", "4200"),
            "000000000001");

    try (LocalRedsysServer server = new LocalRedsysServer(200, responseBody)) {
      RedsysPaymentGateway gateway = gatewayFor(server);

      GatewayRefundResult result =
          gateway.refund(
              new GatewayRefundParameters(
                  "WEB-20260913-000001", "redsys-auth-1", new BigDecimal("42.00")));

      assertThat(result.success()).isFalse();
      assertThat(result.failureReason()).contains("Ds_Response=0190");
    }
  }

  @Test
  void refundReturnsFailureForInvalidResponseSignature() throws IOException {
    Map<String, String> decodedParams =
        Map.of(
            "Ds_Response", "0000",
            "Ds_AuthorisationCode", "654321",
            "Ds_Order", "000000000001");
    String encodedParams =
        Base64.getEncoder()
            .encodeToString(json(decodedParams).getBytes(StandardCharsets.UTF_8));
    String validSignature = RedsysSignature.sign(PROPERTIES.secretKey(), "000000000001", encodedParams);
    String tamperedBody =
        json(
            Map.of(
                "Ds_SignatureVersion", "HMAC_SHA256_V1",
                "Ds_MerchantParameters", encodedParams,
                "Ds_Signature", validSignature + "x"));

    try (LocalRedsysServer server = new LocalRedsysServer(200, tamperedBody)) {
      RedsysPaymentGateway gateway = gatewayFor(server);

      GatewayRefundResult result =
          gateway.refund(
              new GatewayRefundParameters(
                  "WEB-20260913-000001", "redsys-auth-1", new BigDecimal("42.00")));

      assertThat(result.success()).isFalse();
      assertThat(result.failureReason()).contains("signature");
    }
  }

  private RedsysPaymentGateway gatewayFor(LocalRedsysServer server) {
    return new RedsysPaymentGateway(
        withRestUrl(server.url()),
        OBJECT_MAPPER,
        HttpClient.newBuilder().build(),
        Duration.ofSeconds(5));
  }

  private RedsysProperties withRestUrl(String restUrl) {
    return new RedsysProperties(
        PROPERTIES.merchantCode(),
        PROPERTIES.terminal(),
        PROPERTIES.secretKey(),
        PROPERTIES.redirectUrl(),
        restUrl,
        PROPERTIES.notificationUrl(),
        PROPERTIES.urlOk(),
        PROPERTIES.urlKo());
  }

  private String refundResponseBody(Map<String, String> decodedParams, String redsysOrderId) {
    String encodedParams =
        Base64.getEncoder()
            .encodeToString(json(decodedParams).getBytes(StandardCharsets.UTF_8));
    String signature = RedsysSignature.sign(PROPERTIES.secretKey(), redsysOrderId, encodedParams);
    return json(
        Map.of(
            "Ds_SignatureVersion", "HMAC_SHA256_V1",
            "Ds_MerchantParameters", encodedParams,
            "Ds_Signature", signature));
  }

  private String json(Map<String, String> map) {
    try {
      return OBJECT_MAPPER.writeValueAsString(map);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /** Local HTTP server standing in for Redsys's REST endpoint. */
  private static final class LocalRedsysServer implements AutoCloseable {

    private final HttpServer server;

    LocalRedsysServer(int statusCode, String body) throws IOException {
      this(statusCode, body, 0);
    }

    LocalRedsysServer(int statusCode, String body, long delayMillis) throws IOException {
      server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
      server.createContext(
          "/",
          (HttpExchange exchange) -> {
            sleep(delayMillis);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusCode, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
          });
      server.start();
    }

    private static void sleep(long millis) {
      try {
        Thread.sleep(millis);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }

    String url() {
      return "http://localhost:" + server.getAddress().getPort();
    }

    @Override
    public void close() {
      server.stop(0);
    }
  }
}
