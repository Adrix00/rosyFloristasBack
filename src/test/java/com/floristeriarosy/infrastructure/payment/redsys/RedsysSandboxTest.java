package com.floristeriarosy.infrastructure.payment.redsys;

import static org.assertj.core.api.Assertions.assertThat;

import com.floristeriarosy.application.payment.dto.GatewayChargeParameters;
import com.floristeriarosy.application.payment.dto.GatewayRefundParameters;
import com.floristeriarosy.application.payment.dto.GatewayRefundResult;
import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Real calls to Redsys's public sandbox (opt-in: {@code mvn -Psandbox test}). Skipped when the
 * sandbox is unreachable. Advisory, not release-blocking: the sandbox can be down or rate-limit.
 *
 * <p>These are the known-answer check for the signature implementation: a correctly signed charge
 * accepted by Redsys and a tampered one rejected with {@code SIS0042} prove the 3DES/HMAC chain
 * against Redsys itself, which self-consistency unit tests cannot. No fixed known-answer vector
 * exists in the local documentation (Getnet LatAm), so none is invented. The signature version
 * used is {@code HMAC_SHA256_V1}; Redsys's current documentation describes {@code HMAC_SHA512_V2},
 * but the sandbox still accepts V1.
 *
 * <p>{@code charge()} needs a browser to complete, so its test only asserts Redsys does not answer
 * with a signature or order-format error. The refund test authorizes headlessly through the 3DS v2
 * REST handshake with the documented frictionless test card (the sandbox merchant is "secure", so a
 * plain PAN authorization is refused with {@code SIS0218}), then refunds through
 * {@link RedsysPaymentGateway#refund}. It is what showed that a successful refund answers
 * {@code Ds_Response=0900}, not 0000-0099.
 */
@Tag("sandbox")
class RedsysSandboxTest {

  private static final String HOST = "sis-t.redsys.es";
  private static final int PORT = 25443;
  private static final String SIGNATURE_ERROR = "SIS0042";
  private static final String ORDER_FORMAT_ERROR = "SIS0014";
  private static final String AUTHORIZATION_URL =
      "https://" + HOST + ":" + PORT + "/sis/rest/iniciaPeticionREST";
  // Published test card (frictionless 3DS, 4548 8144 7972 7229, expiry 4912, CVV 123):
  // https://pagosonline.redsys.es/desarrolladores-inicio/integrate-con-nosotros/tarjetas-y-entornos-de-prueba/
  private static final String FRICTIONLESS_TEST_CARD = "4548814479727229";
  private static final String TEST_CARD_EXPIRY = "4912";
  private static final String TEST_CARD_CVV = "123";

  private static final RedsysProperties SANDBOX =
      new RedsysProperties(
          "999008881",
          "001",
          "sq7HjrUOBfKmC576ILgskD5srU870gJ7",
          "https://" + HOST + ":" + PORT + "/sis/realizarPago",
          "https://" + HOST + ":" + PORT + "/sis/rest/trataPeticionREST",
          "http://localhost:8080/api/v1/payments/redsys/notification",
          "http://localhost:5173/checkout/ok",
          "http://localhost:5173/checkout/ko");

  private final RedsysPaymentGateway gateway =
      new RedsysPaymentGateway(SANDBOX, new ObjectMapper());

  @BeforeAll
  static void skipWhenSandboxIsUnreachable() {
    Assumptions.assumeTrue(reachable(HOST, PORT), "Redsys sandbox not reachable");
  }

  @Test
  void chargeOutputIsAcceptedByRedsysAsWellFormed() throws Exception {
    String body = postToRedsys(gateway.charge(charge()).formFields());

    assertThat(body).doesNotContain(SIGNATURE_ERROR).doesNotContain(ORDER_FORMAT_ERROR);
  }

  @Test
  void aTamperedSignatureIsRejectedByRedsys() throws Exception {
    Map<String, String> fields = new HashMap<>(gateway.charge(charge()).formFields());
    fields.put("Ds_Signature", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");

    assertThat(postToRedsys(fields)).contains(SIGNATURE_ERROR);
  }

  @Test
  void refundOfARealSandboxAuthorizationIsApprovedWithAVerifiableSignature() throws Exception {
    long sequence = System.currentTimeMillis() % 1_000_000;
    OrderNumber orderNumber = OrderNumber.of("WEB-20260920-" + String.format("%06d", sequence));
    String redsysOrderId = RedsysOrderId.from(orderNumber);

    Map<String, Object> started =
        decodeMerchantParameters(
            callRest(AUTHORIZATION_URL, redsysOrderId, cardParams(redsysOrderId, "CardData", null)));
    assertThat(started).as("3DS start response").containsKey("Ds_EMV3DS");
    Map<String, Object> authorized =
        decodeMerchantParameters(
            callRest(
                SANDBOX.restUrl(),
                redsysOrderId,
                cardParams(redsysOrderId, "AuthenticationData", started)));
    assertThat(String.valueOf(authorized.get("Ds_Response")))
        .as("authorization response: %s", authorized)
        .matches("00\\d\\d");

    GatewayRefundResult refund =
        gateway.refund(
            new GatewayRefundParameters(
                orderNumber.value(),
                String.valueOf(authorized.get("Ds_AuthorisationCode")),
                new BigDecimal("12.50")));

    assertThat(refund.success()).as("refund result: %s", refund).isTrue();
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> cardParams(
      String redsysOrderId, String threeDsInfo, Map<String, Object> started) {
    Map<String, Object> emv3ds = new LinkedHashMap<>();
    emv3ds.put("threeDSInfo", threeDsInfo);
    if (started != null) {
      Map<String, Object> startedEmv3ds = (Map<String, Object>) started.get("Ds_EMV3DS");
      emv3ds.put("protocolVersion", startedEmv3ds.get("protocolVersion"));
      emv3ds.put("threeDSServerTransID", startedEmv3ds.get("threeDSServerTransID"));
      emv3ds.put("threeDSCompInd", "N");
      emv3ds.put("notificationURL", "https://localhost/3ds-notification");
      emv3ds.put("browserAcceptHeader", "text/html,application/xhtml+xml");
      emv3ds.put("browserUserAgent", "Mozilla/5.0");
      emv3ds.put("browserJavaEnabled", "false");
      emv3ds.put("browserJavascriptEnabled", "true");
      emv3ds.put("browserLanguage", "es-ES");
      emv3ds.put("browserColorDepth", "24");
      emv3ds.put("browserScreenHeight", "1080");
      emv3ds.put("browserScreenWidth", "1920");
      emv3ds.put("browserTZ", "-120");
    }
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("DS_MERCHANT_EMV3DS", emv3ds);
    params.put("DS_MERCHANT_AMOUNT", "1250");
    params.put("DS_MERCHANT_ORDER", redsysOrderId);
    params.put("DS_MERCHANT_MERCHANTCODE", SANDBOX.merchantCode());
    params.put("DS_MERCHANT_CURRENCY", "978");
    params.put("DS_MERCHANT_TRANSACTIONTYPE", "0");
    params.put("DS_MERCHANT_TERMINAL", SANDBOX.terminal());
    params.put("DS_MERCHANT_PAN", FRICTIONLESS_TEST_CARD);
    params.put("DS_MERCHANT_EXPIRYDATE", TEST_CARD_EXPIRY);
    params.put("DS_MERCHANT_CVV2", TEST_CARD_CVV);
    return params;
  }

  private String callRest(String url, String redsysOrderId, Map<String, Object> params)
      throws IOException, InterruptedException {
    ObjectMapper mapper = new ObjectMapper();
    String encoded =
        Base64.getEncoder().encodeToString(mapper.writeValueAsBytes(params));
    String body =
        mapper.writeValueAsString(
            Map.of(
                "Ds_SignatureVersion", "HMAC_SHA256_V1",
                "Ds_MerchantParameters", encoded,
                "Ds_Signature", RedsysSignature.sign(SANDBOX.secretKey(), redsysOrderId, encoded)));
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(url))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(20))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).body();
  }

  private Map<String, Object> decodeMerchantParameters(String responseBody) {
    ObjectMapper mapper = new ObjectMapper();
    Map<String, Object> response =
        mapper.readValue(responseBody, new TypeReference<Map<String, Object>>() {});
    assertThat(response).as("Redsys response").containsKey("Ds_MerchantParameters");
    return mapper.readValue(
        RedsysBase64.decode(String.valueOf(response.get("Ds_MerchantParameters"))),
        new TypeReference<Map<String, Object>>() {});
  }

  private GatewayChargeParameters charge() {
    return new GatewayChargeParameters(
        OrderNumber.of("WEB-20260920-" + String.format("%06d", System.nanoTime() % 1_000_000)),
        new BigDecimal("12.50"),
        null,
        "sandbox-idem-key");
  }

  private String postToRedsys(Map<String, String> formFields) throws IOException, InterruptedException {
    String form =
        formFields.entrySet().stream()
            .map(
                e ->
                    URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                        + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
            .collect(Collectors.joining("&"));
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(SANDBOX.redirectUrl()))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .timeout(Duration.ofSeconds(20))
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build();
    HttpClient client =
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    return response.body();
  }

  private static boolean reachable(String host, int port) {
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress(host, port), 3_000);
      return true;
    } catch (IOException e) {
      return false;
    }
  }
}
