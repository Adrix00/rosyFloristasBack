package com.floristeriarosy.application.payment.service;

import com.floristeriarosy.application.order.command.ConfirmCheckoutCommand;
import com.floristeriarosy.application.order.port.in.ConfirmCheckoutUseCase;
import com.floristeriarosy.application.payment.port.in.ReceiveRedsysNotificationUseCase;
import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;
import com.floristeriarosy.infrastructure.payment.redsys.RedsysProperties;
import com.floristeriarosy.infrastructure.payment.redsys.RedsysSignature;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Implements {@link ReceiveRedsysNotificationUseCase}: verify the signature before touching any
 * state, decode {@code Ds_Response} (0000-0099 approves, per Redsys convention), then hand off to
 * {@link ConfirmCheckoutUseCase} — this class knows Redsys's field names, {@code order} does not
 * (ADR-017).
 */
@Service
public class ReceiveRedsysNotificationService implements ReceiveRedsysNotificationUseCase {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ReceiveRedsysNotificationService.class);

  private final RedsysProperties properties;
  private final ObjectMapper objectMapper;
  private final ConfirmCheckoutUseCase confirmCheckoutUseCase;

  /**
   * @param properties holds the merchant signing key
   * @param objectMapper decodes {@code Ds_MerchantParameters}
   * @param confirmCheckoutUseCase resolves the checkout, once verified
   */
  public ReceiveRedsysNotificationService(
      RedsysProperties properties,
      ObjectMapper objectMapper,
      ConfirmCheckoutUseCase confirmCheckoutUseCase) {
    this.properties = properties;
    this.objectMapper = objectMapper.rebuild().build();
    this.confirmCheckoutUseCase = confirmCheckoutUseCase;
  }

  /**
   * @param encodedMerchantParameters the raw {@code Ds_MerchantParameters} field
   * @param receivedSignature the raw {@code Ds_Signature} field
   * @throws IllegalArgumentException the signature does not verify — never processed further
   */
  @Override
  public void execute(String encodedMerchantParameters, String receivedSignature) {
    Map<String, String> params = decode(encodedMerchantParameters);
    String orderNumber =
        params.get("Ds_Merchant_Order") != null
            ? params.get("Ds_Merchant_Order")
            : params.get("Ds_Order");
    LOGGER.debug("receiveRedsysNotification orderNumber={}", orderNumber);
    if (!RedsysSignature.verify(
        properties.secretKey(), orderNumber, encodedMerchantParameters, receivedSignature)) {
      throw new IllegalArgumentException("Invalid Redsys signature for order " + orderNumber);
    }
    String responseCode = params.get("Ds_Response");
    boolean approved =
        responseCode != null && responseCode.matches("0\\d{3}") && Integer.parseInt(responseCode) <= 99;
    String idempotencyKey = params.get("Ds_Merchant_MerchantData");
    confirmCheckoutUseCase.execute(
        new ConfirmCheckoutCommand(
            idempotencyKey,
            OrderNumber.of(orderNumber),
            approved,
            approved ? params.get("Ds_AuthorisationCode") : null,
            approved ? null : "Ds_Response=" + responseCode));
    LOGGER.debug(
        "receiveRedsysNotification orderNumber={} -> approved={}", orderNumber, approved);
  }

  /**
   * @param encodedMerchantParameters the Base64-encoded JSON
   * @return the decoded {@code Ds_*} fields
   */
  private Map<String, String> decode(String encodedMerchantParameters) {
    try {
      byte[] decoded = Base64.getDecoder().decode(encodedMerchantParameters);
      return objectMapper.readValue(
          decoded,
          objectMapper.getTypeFactory().constructMapType(HashMap.class, String.class, String.class));
    } catch (JacksonException e) {
      throw new IllegalArgumentException("Could not decode Ds_MerchantParameters", e);
    }
  }
}
