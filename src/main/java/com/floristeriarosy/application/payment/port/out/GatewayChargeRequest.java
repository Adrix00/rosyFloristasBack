package com.floristeriarosy.application.payment.port.out;

import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;
import java.math.BigDecimal;

/**
 * @param orderNumber becomes {@code Ds_Merchant_Order} (ADR-017)
 * @param amount the amount to charge
 * @param paymentMethodToken the gateway token for a new card
 * @param merchantData becomes {@code Ds_Merchant_MerchantData}, carrying the checkout's
 *     {@code Idempotency-Key} back through the notification (ADR-017)
 */
public record GatewayChargeRequest(
    OrderNumber orderNumber, BigDecimal amount, String paymentMethodToken, String merchantData) {}
