package com.floristeriarosy.application.order.command;

import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;

/**
 * @param idempotencyKey Redsys's echoed {@code Ds_Merchant_MerchantData} (ADR-017) — finds the
 *     Fase-1 lock
 * @param orderNumber Redsys's echoed {@code Ds_Merchant_Order}
 * @param approved whether Redsys approved the charge
 * @param providerPaymentIntentId Redsys's authorization reference, present only when {@code
 *     approved}
 * @param failureReason why Redsys declined, present only when not {@code approved}
 */
public record ConfirmCheckoutCommand(
    String idempotencyKey,
    OrderNumber orderNumber,
    boolean approved,
    String providerPaymentIntentId,
    String failureReason) {}
