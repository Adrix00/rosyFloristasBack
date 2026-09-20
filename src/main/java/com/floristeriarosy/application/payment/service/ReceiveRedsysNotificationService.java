package com.floristeriarosy.application.payment.service;

import com.floristeriarosy.application.order.command.ConfirmCheckoutCommand;
import com.floristeriarosy.application.order.dto.ConfirmCheckoutOutcome;
import com.floristeriarosy.application.order.dto.IdempotencyLock;
import com.floristeriarosy.application.order.dto.PendingCheckoutPayload;
import com.floristeriarosy.application.order.port.in.ConfirmCheckoutUseCase;
import com.floristeriarosy.application.order.port.out.CheckoutIdempotencyPort;
import com.floristeriarosy.application.payment.dto.GatewayRefundResult;
import com.floristeriarosy.application.payment.port.in.ReceiveRedsysNotificationUseCase;
import com.floristeriarosy.application.payment.port.out.PaymentGatewayPort;
import com.floristeriarosy.application.shared.port.out.PiiCryptoPort;
import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;
import com.floristeriarosy.infrastructure.payment.redsys.RedsysBase64;
import com.floristeriarosy.infrastructure.payment.redsys.RedsysProperties;
import com.floristeriarosy.infrastructure.payment.redsys.RedsysResponseCode;
import com.floristeriarosy.infrastructure.payment.redsys.RedsysSignature;
import java.math.BigDecimal;
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
 * state, decode {@code Ds_Response} (0000-0099 approves, per Redsys convention), recover the business
 * order number from the Fase-1 payload, then hand off to {@link ConfirmCheckoutUseCase} — this class
 * knows Redsys's field names, {@code order} does not (ADR-017).
 *
 * <p>{@code Ds_Order} is only a 12-digit technical id used for signature diversification; the real
 * business order number lives in the encrypted payload, keyed by {@code Ds_Merchant_MerchantData}.
 */
@Service
public class ReceiveRedsysNotificationService implements ReceiveRedsysNotificationUseCase {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ReceiveRedsysNotificationService.class);

  private final RedsysProperties properties;
  private final ObjectMapper objectMapper;
  private final CheckoutIdempotencyPort idempotencyPort;
  private final PiiCryptoPort piiCryptoPort;
  private final ConfirmCheckoutUseCase confirmCheckoutUseCase;
  private final PaymentGatewayPort gatewayPort;

  /**
   * @param properties holds the merchant signing key
   * @param objectMapper decodes {@code Ds_MerchantParameters}
   * @param idempotencyPort reads the Fase-1 lock by {@code Ds_Merchant_MerchantData}
   * @param piiCryptoPort decrypts the pending payload so the business order number can be recovered
   * @param confirmCheckoutUseCase resolves the checkout, once verified
   * @param gatewayPort refunds a charge approved after its checkout expired
   */
  public ReceiveRedsysNotificationService(
      RedsysProperties properties,
      ObjectMapper objectMapper,
      CheckoutIdempotencyPort idempotencyPort,
      PiiCryptoPort piiCryptoPort,
      ConfirmCheckoutUseCase confirmCheckoutUseCase,
      PaymentGatewayPort gatewayPort) {
    this.properties = properties;
    this.objectMapper = objectMapper.rebuild().build();
    this.idempotencyPort = idempotencyPort;
    this.piiCryptoPort = piiCryptoPort;
    this.confirmCheckoutUseCase = confirmCheckoutUseCase;
    this.gatewayPort = gatewayPort;
  }

  /**
   * @param encodedMerchantParameters the raw {@code Ds_MerchantParameters} field
   * @param receivedSignature the raw {@code Ds_Signature} field
   * @throws IllegalArgumentException the signature does not verify — never processed further
   */
  @Override
  public void execute(String encodedMerchantParameters, String receivedSignature) {
    Map<String, String> params = decode(encodedMerchantParameters);
    String redsysOrderId =
        params.get("Ds_Merchant_Order") != null
            ? params.get("Ds_Merchant_Order")
            : params.get("Ds_Order");
    LOGGER.debug("receiveRedsysNotification redsysOrderId={}", redsysOrderId);
    if (redsysOrderId == null) {
      throw new IllegalArgumentException("Redsys notification carries no order id");
    }
    if (!RedsysSignature.verify(
        properties.secretKey(), redsysOrderId, encodedMerchantParameters, receivedSignature)) {
      throw new IllegalArgumentException("Invalid Redsys signature for order " + redsysOrderId);
    }

    String idempotencyKey = params.get("Ds_Merchant_MerchantData");
    LOGGER.debug(
        "receiveRedsysNotification idempotencyKey={}", Encode.forJava(idempotencyKey));
    String responseCode = params.get("Ds_Response");
    boolean approved = RedsysResponseCode.isApproved(responseCode);
    IdempotencyLock lock = idempotencyPort.find(idempotencyKey).orElse(null);
    if (lock != null && "FAILED".equals(lock.status())) {
      handleExpired(idempotencyKey, redsysOrderId, params, approved);
      return;
    }
    if (lock == null || !"PENDING".equals(lock.status())) {
      LOGGER.debug(
          "receiveRedsysNotification idempotencyKey={} -> no pending lock, ignoring",
          Encode.forJava(idempotencyKey));
      return;
    }

    ConfirmCheckoutOutcome outcome =
        confirmCheckoutUseCase.execute(
            new ConfirmCheckoutCommand(
                idempotencyKey,
                orderNumberFrom(lock),
                approved,
                approved ? params.get("Ds_AuthorisationCode") : null,
                approved ? null : "Ds_Response=" + responseCode));
    if (outcome == ConfirmCheckoutOutcome.EXPIRED) {
      handleExpired(idempotencyKey, redsysOrderId, params, approved);
    }
    LOGGER.debug(
        "receiveRedsysNotification idempotencyKey={} -> approved={} outcome={}",
        Encode.forJava(idempotencyKey),
        approved,
        outcome);
  }

  /**
   * A notification for a checkout the cleanup already released. A declined one changes nothing. An
   * approved one means Redsys charged the customer and no order exists: refund it automatically from
   * the notification's own data (there is no {@code payments} row) and log at ERROR so an operator
   * sees it. Never throws: Redsys must still get its 200 so it stops retrying, and if the refund
   * fails too the ERROR log is the alert.
   *
   * @param idempotencyKey Redsys's echoed {@code Ds_Merchant_MerchantData}
   * @param redsysOrderId the 12-digit {@code Ds_Order} the charge was made under
   * @param params the verified notification fields
   * @param approved whether the notification approved the charge
   */
  private void handleExpired(
      String idempotencyKey, String redsysOrderId, Map<String, String> params, boolean approved) {
    if (!approved) {
      LOGGER.debug(
          "receiveRedsysNotification idempotencyKey={} -> declined after expiry, ignoring",
          Encode.forJava(idempotencyKey));
      return;
    }
    String refundOutcome;
    try {
      BigDecimal amount = BigDecimal.valueOf(Long.parseLong(params.get("Ds_Amount")), 2);
      GatewayRefundResult result = gatewayPort.refundByGatewayOrderId(redsysOrderId, amount);
      refundOutcome = result.success() ? "refunded" : "refund FAILED: " + result.failureReason();
    } catch (RuntimeException e) {
      LOGGER.error("receiveRedsysNotification late approval refund threw", e);
      refundOutcome = "refund NOT attempted (unreadable Ds_Amount or gateway error)";
    }
    LOGGER.error(
        "receiveRedsysNotification idempotencyKey={} redsysOrderId={} -> APPROVED after the checkout"
            + " expired, the customer was charged with no order: {}",
        Encode.forJava(idempotencyKey),
        Encode.forJava(redsysOrderId),
        refundOutcome);
  }

  /**
   * @param lock the {@code PENDING} checkout row
   * @return the business order number stored in its Fase-1 payload
   */
  private OrderNumber orderNumberFrom(IdempotencyLock lock) {
    try {
      PendingCheckoutPayload payload =
          objectMapper.readValue(
              piiCryptoPort.decrypt(lock.checkoutPayloadEncrypted()), PendingCheckoutPayload.class);
      return OrderNumber.of(payload.orderNumber());
    } catch (JacksonException e) {
      throw new IllegalStateException("Could not deserialize the pending checkout payload", e);
    }
  }

  /**
   * @param encodedMerchantParameters the Base64-encoded JSON, possibly URL-safe
   * @return the decoded {@code Ds_*} fields
   */
  private Map<String, String> decode(String encodedMerchantParameters) {
    try {
      byte[] decoded = RedsysBase64.decode(encodedMerchantParameters);
      return objectMapper.readValue(
          decoded,
          objectMapper.getTypeFactory().constructMapType(HashMap.class, String.class, String.class));
    } catch (JacksonException e) {
      throw new IllegalArgumentException("Could not decode Ds_MerchantParameters", e);
    }
  }
}
