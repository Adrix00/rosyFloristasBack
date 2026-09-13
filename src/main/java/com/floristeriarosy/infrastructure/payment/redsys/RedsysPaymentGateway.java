package com.floristeriarosy.infrastructure.payment.redsys;

import com.floristeriarosy.application.payment.dto.GatewayChargeParameters;
import com.floristeriarosy.application.payment.dto.GatewayRefundParameters;
import com.floristeriarosy.application.payment.dto.GatewayRefundResult;
import com.floristeriarosy.application.payment.dto.RedsysRedirect;
import com.floristeriarosy.application.payment.port.out.PaymentGatewayPort;
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

  private final RedsysProperties properties;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;

  /**
   * @param properties Redsys merchant configuration
   * @param objectMapper builds {@code Ds_MerchantParameters}' JSON
   */
  public RedsysPaymentGateway(RedsysProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper.rebuild().build();
    this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  }

  /**
   * @param parameters what to charge
   * @return the signed redirect the customer must be sent to
   */
  @Override
  public RedsysRedirect charge(GatewayChargeParameters parameters) {
    LOGGER.debug("charge orderNumber={} amount={}", parameters.orderNumber(), parameters.amount());
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("Ds_Merchant_Amount", toCents(parameters.amount()));
    params.put("Ds_Merchant_Order", parameters.orderNumber().value());
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
    String signature = RedsysSignature.sign(properties.secretKey(), parameters.orderNumber().value(), encodedParams);
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
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("Ds_Merchant_Amount", toCents(parameters.amount()));
    params.put("Ds_Merchant_Order", parameters.orderNumber());
    params.put("Ds_Merchant_MerchantCode", properties.merchantCode());
    params.put("Ds_Merchant_Currency", CURRENCY_EUR);
    params.put("Ds_Merchant_TransactionType", TRANSACTION_TYPE_REFUND);
    params.put("Ds_Merchant_Terminal", properties.terminal());
    String encodedParams = encodeParameters(params);
    String signature = RedsysSignature.sign(properties.secretKey(), parameters.orderNumber(), encodedParams);
    String body = buildRestBody(encodedParams, signature);
    try {
      HttpRequest httpRequest =
          HttpRequest.newBuilder(URI.create(properties.restUrl()))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(body))
              .build();
      HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
      GatewayRefundResult result = parseRefundResponse(response.body());
      LOGGER.debug("refund orderNumber={} -> success={}", parameters.orderNumber(), result.success());
      return result;
    } catch (java.io.IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      LOGGER.debug("refund orderNumber={} -> gateway call failed: {}", parameters.orderNumber(), e.getMessage());
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
   * @return the parsed refund outcome; a response Redsys does not consider a success (a {@code
   *     Ds_Response} outside 0000-0099) is reported as {@code success=false}
   */
  private GatewayRefundResult parseRefundResponse(String responseBody) {
    try {
      @SuppressWarnings("unchecked")
      Map<String, String> response = objectMapper.readValue(responseBody, Map.class);
      String encodedParams = response.get("Ds_MerchantParameters");
      @SuppressWarnings("unchecked")
      Map<String, String> decodedParams =
          objectMapper.readValue(Base64.getDecoder().decode(encodedParams), Map.class);
      String responseCode = decodedParams.get("Ds_Response");
      boolean success =
          responseCode != null && responseCode.matches("0\\d{3}") && Integer.parseInt(responseCode) <= 99;
      return success
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
