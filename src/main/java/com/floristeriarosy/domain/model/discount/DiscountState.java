package com.floristeriarosy.domain.model.discount;

/**
 * Derived lifecycle state of a {@link Discount}, computed from {@code startsAt}/{@code endsAt}/
 * {@code quantitySold}/{@code quantityLimit} against "now" — never persisted (product-discounts.md,
 * section 6).
 */
public enum DiscountState {

  /** {@code startsAt} is in the future. */
  SCHEDULED,

  /** Currently within its vigency window, with units still available. */
  ACTIVE,

  /** Currently within its vigency window, but {@code quantitySold} reached {@code quantityLimit}. */
  SOLD_OUT,

  /** {@code endsAt} has already passed. */
  ENDED
}
