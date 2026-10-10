package com.floristeriarosy.application.order.dto;

/**
 * @param status {@code PENDING}, {@code COMPLETED} or {@code FAILED}
 * @param orderNumber the confirmed order's number, present only when {@code COMPLETED}
 * @param reason why it failed, present only when {@code FAILED}
 */
public record CheckoutStatusDto(String status, String orderNumber, String reason) {}
