package com.floristeriarosy.application.discount.port.out;

import com.floristeriarosy.domain.model.discount.valueobject.DiscountId;

/**
 * Reserves and releases promotional units with a conditional {@code UPDATE}, never reading first
 * (product-discounts.md, section 3.5/3.6). Consumed by {@code order.md}'s checkout and
 * cancellation flows, not by any use case in this module (section 7, section 11) — it exists so
 * the order module never writes SQL directly against a table it does not own.
 */
public interface DiscountReservationPort {

  /**
   * Reserves {@code quantity} promotional units, in the same transaction as the order that
   * consumes them. Reserves nothing and always succeeds when the discount has no {@code
   * quantityLimit} — there is no counter to exhaust.
   *
   * @param id the discount being purchased under
   * @param quantity the number of units being purchased
   * @return {@code true} if the reservation succeeded, {@code false} if zero rows were affected —
   *     the discount expired or its limit was reached between the moment the caller saw the price
   *     and confirmed it
   */
  boolean reserve(DiscountId id, int quantity);

  /**
   * Returns {@code quantity} previously reserved units, in the same transaction as the order's
   * state change. A no-op if the discount row no longer exists (product-discounts.md, section 3.6:
   * {@code discount_id} may already be {@code NULL}).
   *
   * @param id the discount to release units back to
   * @param quantity the number of units to return
   */
  void release(DiscountId id, int quantity);
}
