package com.floristeriarosy.application.payment.command;

import com.floristeriarosy.domain.model.order.valueobject.OrderId;
import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;
import com.floristeriarosy.domain.model.payment.PaymentMethod;
import java.math.BigDecimal;

/**
 * @param orderId the order being charged
 * @param orderNumber the business identifier, becomes {@code Ds_Merchant_Order} for {@code
 *     CARD_ONLINE} (ADR-017)
 * @param amount the amount to charge
 * @param method how the customer pays
 * @param paymentMethodToken the gateway token for a new card, required for {@code CARD_ONLINE}
 * @param idempotencyKey becomes {@code Ds_Merchant_MerchantData} (ADR-017)
 */
public record ChargeOrderPaymentCommand(
    OrderId orderId,
    OrderNumber orderNumber,
    BigDecimal amount,
    PaymentMethod method,
    String paymentMethodToken,
    String idempotencyKey) {}
