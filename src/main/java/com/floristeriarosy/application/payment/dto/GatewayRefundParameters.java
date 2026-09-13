package com.floristeriarosy.application.payment.dto;

import java.math.BigDecimal;

/**
 * @param orderNumber the original charge's order number, needed to diversify the signature
 *     (ADR-017)
 * @param providerPaymentIntentId the original charge's gateway reference
 * @param amount the full amount to refund (payment.md, section 3.3: always total)
 */
public record GatewayRefundParameters(
    String orderNumber, String providerPaymentIntentId, BigDecimal amount) {}
