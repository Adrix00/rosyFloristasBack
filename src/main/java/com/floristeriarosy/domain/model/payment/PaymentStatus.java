package com.floristeriarosy.domain.model.payment;

/** Lifecycle of a payment (payment.md, section 2), independent of {@code orders.status}. */
public enum PaymentStatus {
  PENDING,
  AUTHORIZED,
  CAPTURED,
  CANCELED,
  FAILED
}
