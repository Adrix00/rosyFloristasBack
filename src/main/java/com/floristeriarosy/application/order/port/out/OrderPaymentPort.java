package com.floristeriarosy.application.order.port.out;

import com.floristeriarosy.application.order.dto.ChargeResult;
import com.floristeriarosy.domain.model.order.valueobject.OrderId;
import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;
import com.floristeriarosy.domain.model.payment.Payment;
import com.floristeriarosy.domain.model.payment.PaymentMethod;
import java.math.BigDecimal;

/**
 * The frontier between {@code order} and payment capture (order.md, section 8; payment.md, section
 * 1): {@code order} asks "charge this" and "refund this" without knowing whether Redsys, cash, or
 * any other provider is behind it — same principle as {@code ProductInventoryPort} facing {@code
 * inventory}.
 *
 * <p>The methods use primitive parameters rather than command records so that {@code
 * infrastructure.persistence.adapter} implementations can satisfy ArchUnit's rule that
 * persistence adapters must not depend on {@code application..command} packages.
 */
public interface OrderPaymentPort {

  /**
   * @param orderId the order being charged
   * @param orderNumber the business identifier — becomes {@code Ds_Merchant_Order} for {@code
   *     CARD_ONLINE} (ADR-017)
   * @param amount the amount to charge
   * @param method how the customer pays
   * @param paymentMethodToken the gateway token for a new card, required when {@code method} is
   *     {@code CARD_ONLINE}; saved cards (Phase 6) are not yet supported
   * @param idempotencyKey the checkout's {@code Idempotency-Key} — becomes {@code
   *     Ds_Merchant_MerchantData} so the Redsys notification can find its way back (ADR-017)
   * @return a redirect to complete payment, or an already-settled charge
   */
  ChargeResult charge(
      OrderId orderId,
      OrderNumber orderNumber,
      BigDecimal amount,
      PaymentMethod method,
      String paymentMethodToken,
      String idempotencyKey);

  /**
   * @param orderId the order whose payment must be refunded
   * @param orderNumber the original charge's business identifier, needed by Redsys for refund
   *     signature diversification (ADR-017)
   * @return the refunded payment
   */
  Payment refund(OrderId orderId, OrderNumber orderNumber);

  /**
   * @param orderId the order being confirmed
   * @param method how the customer paid — always {@code CARD_ONLINE} in practice (order.md §3.2,
   *     Fase 3a); the parameter stays general because {@link Payment#initiate} takes it
   * @param amount the confirmed amount
   * @param providerPaymentIntentId Redsys's authorization reference from the notification
   * @return the saved, {@code CAPTURED} payment
   */
  Payment confirmCharge(OrderId orderId, PaymentMethod method, BigDecimal amount, String providerPaymentIntentId);
}
