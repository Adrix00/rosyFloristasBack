package com.floristeriarosy.application.order.dto;

/** What resolving a Redsys notification found under the checkout row's lock (ADR-017). */
public enum ConfirmCheckoutOutcome {
  /** The checkout was {@code PENDING} and is now resolved: order created, or reservation released. */
  RESOLVED,
  /** Nothing to do: the row is unknown or already {@code COMPLETED} (a Redsys retry). */
  IGNORED,
  /**
   * The notification does not belong to a live attempt: the row is {@code FAILED} (a decline or the
   * abandoned-checkout cleanup already released the reservation) or it was re-armed for a new
   * attempt. No order exists for this charge, so an approved notification landing here means the
   * customer was charged for nothing.
   */
  EXPIRED
}
