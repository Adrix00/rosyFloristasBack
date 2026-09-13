package com.floristeriarosy.application.order.port.out;

import com.floristeriarosy.domain.model.order.valueobject.OrderId;
import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;
import com.floristeriarosy.domain.model.payment.PaymentMethod;
import java.math.BigDecimal;

/**
 * What {@code order} asks {@link OrderPaymentPort#charge} to do (order.md, section 3.2;
 * payment.md, section 3.2).
 *
 * @param orderId the order being charged
 * @param orderNumber the business identifier — becomes {@code Ds_Merchant_Order} for {@code
 *     CARD_ONLINE} (ADR-017)
 * @param amount the amount to charge
 * @param method how the customer pays
 * @param paymentMethodToken the gateway token for a new card, required when {@code method} is
 *     {@code CARD_ONLINE}; saved cards (Phase 6) are not yet supported by this command
 * @param idempotencyKey the checkout's {@code Idempotency-Key} — becomes {@code
 *     Ds_Merchant_MerchantData} so the Redsys notification can find its way back (ADR-017)
 */
public record ChargeCommand(
    OrderId orderId,
    OrderNumber orderNumber,
    BigDecimal amount,
    PaymentMethod method,
    String paymentMethodToken,
    String idempotencyKey) {}
