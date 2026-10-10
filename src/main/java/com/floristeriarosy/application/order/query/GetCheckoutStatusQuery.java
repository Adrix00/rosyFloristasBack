package com.floristeriarosy.application.order.query;

/**
 * @param idempotencyKey the {@code Idempotency-Key} used on the original {@code POST /checkout}
 */
public record GetCheckoutStatusQuery(String idempotencyKey) {}
