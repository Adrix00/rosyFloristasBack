package com.floristeriarosy.domain.model.order;

/**
 * Fulfillment status of an order — accept, prepare, deliver (order.md, section 3.9). This is
 * separate from payment status, which lives in {@code payments} ({@code payment.md}).
 */
public enum OrderStatus {
  PENDING,
  PREPARING,
  OUT_FOR_DELIVERY,
  DELIVERED,
  REJECTED,
  CANCELLED
}
