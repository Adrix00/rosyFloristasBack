package com.floristeriarosy.infrastructure.web.response.order;

/**
 * @param status {@code PENDING}, {@code COMPLETED} or {@code FAILED}
 * @param orderNumber present only when {@code COMPLETED}
 * @param reason present only when {@code FAILED}
 */
public record CheckoutStatusResponse(String status, String orderNumber, String reason) {}
