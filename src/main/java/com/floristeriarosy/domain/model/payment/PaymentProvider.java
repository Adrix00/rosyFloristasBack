package com.floristeriarosy.domain.model.payment;

/**
 * Who actually captures the charge (payment.md, rule 3.1). {@code payments.provider} has no
 * database {@code CHECK} constraint — this enum is the domain-side safety net.
 */
public enum PaymentProvider {
  /** {@code CARD_ONLINE}, via the Redsys TPV Virtual redirect (ADR-017). */
  REDSYS,
  /** {@code CASH}, {@code DATAPHONE}, {@code INTERFLORA} — captured directly, no gateway call. */
  MANUAL
}
