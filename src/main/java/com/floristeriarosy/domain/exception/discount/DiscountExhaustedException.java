package com.floristeriarosy.domain.exception.discount;

import com.floristeriarosy.domain.exception.ConflictException;
import com.floristeriarosy.domain.exception.HasErrorCode;

/**
 * A unit reservation found no units available: the discount expired or its {@code quantityLimit}
 * was reached between the moment the caller saw the price and confirmed it (product-discounts.md,
 * section 3.5). Raised by {@code DiscountReservationPort}'s caller (order.md), not by this module.
 */
public final class DiscountExhaustedException extends ConflictException implements HasErrorCode {

  /**
   * @param message a message for a person; never exposed raw to the API client
   */
  public DiscountExhaustedException(String message) {
    super(message);
  }

  @Override
  public String errorCode() {
    return DiscountErrorCode.DISCOUNT_EXHAUSTED.name();
  }
}
