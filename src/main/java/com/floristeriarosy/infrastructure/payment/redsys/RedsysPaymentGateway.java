package com.floristeriarosy.infrastructure.payment.redsys;

import com.floristeriarosy.application.payment.dto.GatewayChargeParameters;
import com.floristeriarosy.application.payment.dto.GatewayRefundParameters;
import com.floristeriarosy.application.payment.dto.GatewayRefundResult;
import com.floristeriarosy.application.payment.dto.RedsysRedirect;
import com.floristeriarosy.application.payment.port.out.PaymentGatewayPort;
import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Implements {@link PaymentGatewayPort} against Redsys TPV Virtual (ADR-017). {@link #charge} is a
 * pure computation — Redsys's redirect model means the "call" is the browser posting a signed form,
 * this backend never talks to Redsys over the network to start a charge. {@link #refund} does make
 * a real server-to-server call, since a merchant-initiated refund has no redirect step.
 */
@Component
public class RedsysPaymentGateway implements PaymentGatewayPort {

  private static final Logger LOGGER = LoggerFactory.getLogger(RedsysPaymentGateway.class);
  private static final String SIGNATURE_VERSION = "HMAC_SHA256_V1";
  private static final String CURRENCY_EUR = "978";
  private static final String TRANSACTION_TYPE_AUTHORIZATION = "0";
  private static final String TRANSACTION_TYPE_REFUND = "3";
  private static final Duration REFUND_TIMEOUT = Duration.ofSeconds(30);

  private final RedsysProperties properties;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;
  private final Duration refundTimeout;

  /**
   * @param properties Redsys merchant configuration
   * @param objectMapper builds {@code Ds_MerchantParameters}' JSON; rebuilt to avoid storing the
   *     caller's mutable reference directly (SpotBugs EI_EXPOSE_REP2)
   */
  @Autowired
  public RedsysPaymentGateway(RedsysProperties properties, ObjectMapper objectMapper) {
    this(properties, objectMapper, defaultHttpClient(), REFUND_TIMEOUT);
  }

  /**
   * @param properties Redsys merchant configuration
   * @param objectMapper builds {@code Ds_MerchantParameters}' JSON
   * @param httpClient the HTTP client used for refund calls; exposed for tests
   * @param refundTimeout how long a refund call may wait for Redsys's response
   */
  RedsysPaymentGateway(
      RedsysProperties properties,
      ObjectMapper objectMapper,
      HttpClient httpClient,
      Duration refundTimeout) {
    this.properties = properties;
    this.objectMapper = objectMapper.rebuild().build();
    this.httpClient = httpClient;
    this.refundTimeout = refundTimeout;
  }

  /**
   * @return a preconfigured {@link HttpClient} with a connection timeout
   */
  private static HttpClient defaultHttpClient() {
    return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  }

  /**
   * @param parameters what to charge
   * @return the signed redirect the customer must be sent to
   */
  @Override
  public RedsysRedirect charge(GatewayChargeParameters parameters) {
    LOGGER.debug("charge orderNumber={} amount={}", parameters.orderNumber(), parameters.amount());
    String redsysOrderId = RedsysOrderId.from(parameters.orderNumber());
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("Ds_Merchant_Amount", toCents(parameters.amount()));
    params.put("Ds_Merchant_Order", redsysOrderId);
    params.put("Ds_Merchant_MerchantCode", properties.merchantCode());
    params.put("Ds_Merchant_Currency", CURRENCY_EUR);
    params.put("Ds_Merchant_TransactionType", TRANSACTION_TYPE_AUTHORIZATION);
    params.put("Ds_Merchant_Terminal", properties.terminal());
    params.put("Ds_Merchant_MerchantURL", properties.notificationUrl());
    params.put("Ds_Merchant_UrlOK", properties.urlOk());
    params.put("Ds_Merchant_UrlKO", properties.urlKo());
    params.put("Ds_Merchant_MerchantData", parameters.merchantData());
    params.put("Ds_Merchant_Identifier", parameters.paymentMethodToken());
    String encodedParams = encodeParameters(params);
    String signature = RedsysSignature.sign(properties.secretKey(), redsysOrderId, encodedParams);
    Map<String, String> formFields =
        Map.of(
            "Ds_SignatureVersion", SIGNATURE_VERSION,
            "Ds_MerchantParameters", encodedParams,
            "Ds_Signature", signature);
    LOGGER.debug("charge orderNumber={} -> redirect built", parameters.orderNumber());
    return new RedsysRedirect(properties.redirectUrl(), formFields);
  }

  /**
   * @param parameters what to refund
   * @return whether Redsys confirmed the refund
   */
  @Override
  public GatewayRefundResult refund(GatewayRefundParameters parameters) {
    LOGGER.debug("refund orderNumber={} amount={}", parameters.orderNumber(), parameters.amount());
    return refundOrder(RedsysOrderId.from(OrderNumber.of(parameters.orderNumber())), parameters.amount());
  }

  /**
   * @param gatewayOrderId the 12-digit {@code Ds_Merchant_Order} Redsys received with the charge
   * @param amount the full amount charged
   * @return whether Redsys confirmed the refund
   */
  @Override
  public GatewayRefundResult refundByGatewayOrderId(String gatewayOrderId, BigDecimal amount) {
    LOGGER.debug("refundByGatewayOrderId redsysOrderId={} amount={}", gatewayOrderId, amount);
    return refundOrder(gatewayOrderId, amount);
  }

  /**
   * @param redsysOrderId the 12-digit {@code Ds_Merchant_Order} the charge was made under
   * @param amount the full amount to refund
   * @return whether Redsys confirmed the refund; a rejection, non-200, malformed body, connection
   *     failure or timeout is a failed result, never an exception
   */
  private GatewayRefundResult refundOrder(String redsysOrderId, BigDecimal amount) {
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("Ds_Merchant_Amount", toCents(amount));
    params.put("Ds_Merchant_Order", redsysOrderId);
    params.put("Ds_Merchant_MerchantCode", properties.merchantCode());
    params.put("Ds_Merchant_Currency", CURRENCY_EUR);
    params.put("Ds_Merchant_TransactionType", TRANSACTION_TYPE_REFUND);
    params.put("Ds_Merchant_Terminal", properties.terminal());
    String encodedParams = encodeParameters(params);
    String signature = RedsysSignature.sign(properties.secretKey(), redsysOrderId, encodedParams);
    String body = buildRestBody(encodedParams, signature);
    try {
      HttpRequest httpRequest =
          HttpRequest.newBuilder(URI.create(properties.restUrl()))
              .header("Content-Type", "application/json")
              .timeout(refundTimeout)
              .POST(HttpRequest.BodyPublishers.ofString(body))
              .build();
      HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        LOGGER.debug("refund redsysOrderId={} -> HTTP {}", redsysOrderId, response.statusCode());
        return new GatewayRefundResult(false, null, "Redsys returned HTTP " + response.statusCode());
      }
      GatewayRefundResult result = parseRefundResponse(response.body(), redsysOrderId);
      LOGGER.debug("refund redsysOrderId={} -> success={}", redsysOrderId, result.success());
      return result;
    } catch (java.io.IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      LOGGER.debug("refund redsysOrderId={} -> gateway call failed: {}", redsysOrderId, e.getMessage());
      return new GatewayRefundResult(false, null, "Redsys call failed: " + e.getMessage());
    }
  }

  /**
   * @param params the {@code Ds_Merchant_*} fields to encode
   * @return the Base64-encoded {@code Ds_MerchantParameters} JSON
   */
  private String encodeParameters(Map<String, Object> params) {
    try {
      return Base64.getEncoder().encodeToString(objectMapper.writeValueAsBytes(params));
    } catch (JacksonException e) {
      throw new IllegalStateException("Could not serialize Ds_MerchantParameters", e);
    }
  }

  /**
   * @param encodedParams the already Base64-encoded {@code Ds_MerchantParameters}
   * @param signature the computed {@code Ds_Signature}
   * @return the JSON body Redsys's REST endpoint expects
   */
  private String buildRestBody(String encodedParams, String signature) {
    try {
      Map<String, String> body =
          Map.of(
              "Ds_SignatureVersion", SIGNATURE_VERSION,
              "Ds_MerchantParameters", encodedParams,
              "Ds_Signature", signature);
      return objectMapper.writeValueAsString(body);
    } catch (JacksonException e) {
      throw new IllegalStateException("Could not serialize the REST request body", e);
    }
  }

  /**
   * @param responseBody the raw REST response body
   * @param redsysOrderId the order id Redsys received, used to verify the response signature
   * @return the parsed refund outcome; a response Redsys does not consider a success (a {@code
   *     Ds_Response} outside 0000-0099) or with an invalid signature is reported as
   *     {@code success=false}
   */
  private GatewayRefundResult parseRefundResponse(String responseBody, String redsysOrderId) {
    try {
      @SuppressWarnings("unchecked")
      Map<String, String> response = objectMapper.readValue(responseBody, Map.class);
      String encodedParams = response.get("Ds_MerchantParameters");
      if (encodedParams == null) {
        String errorCode = response.get("errorCode");
        LOGGER.debug("refund redsysOrderId={} -> rejection errorCode={}", redsysOrderId, errorCode);
        return new GatewayRefundResult(false, null, "Redsys rejected the refund: " + errorCode);
      }
      String receivedSignature = response.get("Ds_Signature");
      if (receivedSignature == null
          || !RedsysSignature.verify(properties.secretKey(), redsysOrderId, encodedParams, receivedSignature)) {
        return new GatewayRefundResult(false, null, "Invalid Redsys refund response signature");
      }
      @SuppressWarnings("unchecked")
      Map<String, String> decodedParams =
          objectMapper.readValue(RedsysBase64.decode(encodedParams), Map.class);
      String responseCode = decodedParams.get("Ds_Response");
      return RedsysResponseCode.isRefundApproved(responseCode)
          ? new GatewayRefundResult(true, decodedParams.get("Ds_AuthorisationCode"), null)
          : new GatewayRefundResult(false, null, "Ds_Response=" + responseCode);
    } catch (JacksonException e) {
      return new GatewayRefundResult(false, null, "Could not parse Redsys response: " + e.getMessage());
    }
  }

  /**
   * @param amount the amount in euros
   * @return the amount in cents, as Redsys's integer-string format requires
   */
  private String toCents(BigDecimal amount) {
    return amount.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).toBigInteger().toString();
  }
}
