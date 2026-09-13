package com.floristeriarosy.application.payment.dto;

/**
 * @param success whether the gateway confirmed the refund
 * @param providerReference the gateway's refund id, present only when {@code success}
 * @param failureReason why the gateway refused, present only when not {@code success}
 */
public record GatewayRefundResult(boolean success, String providerReference, String failureReason) {}
