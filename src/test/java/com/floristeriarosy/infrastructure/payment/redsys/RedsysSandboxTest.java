package com.floristeriarosy.infrastructure.payment.redsys;

import static org.assertj.core.api.Assertions.assertThat;

import com.floristeriarosy.application.payment.dto.GatewayChargeParameters;
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
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * Real calls to Redsys's public sandbox (opt-in: {@code mvn -Psandbox test}). A browser is needed to
 * complete a payment, so these only assert that Redsys accepts what {@code charge()} produces as
 * well-formed, or rejects a tampered signature. Skipped when the sandbox is unreachable.
 *
 * <p>These are the known-answer check for the signature implementation: a correctly signed charge
 * accepted by Redsys and a tampered one rejected with {@code SIS0042} prove the 3DES/HMAC chain
 * against Redsys itself, which self-consistency unit tests cannot. No fixed known-answer vector
 * exists in the local documentation (Getnet LatAm), so none is invented.
 *
 * <p>The REST authorization-then-refund round trip is not covered: the local Redsys documentation
 * describes no sandbox test card or authorization flow for it.
 */
@Tag("sandbox")
class RedsysSandboxTest {

  private static final String HOST = "sis-t.redsys.es";
  private static final int PORT = 25443;
  private static final String SIGNATURE_ERROR = "SIS0042";
  private static final String ORDER_FORMAT_ERROR = "SIS0014";

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
