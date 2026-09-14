package com.floristeriarosy.domain.exception.order;

import com.floristeriarosy.domain.exception.ConflictException;
import com.floristeriarosy.domain.exception.HasErrorCode;

/** Fase 1: the promotion sold out between the cart price-view and payment (order.md, §9). */
public final class CheckoutDiscountExhaustedException extends ConflictException implements HasErrorCode {

  /**
   * @param message what the caller should see
   */
  public CheckoutDiscountExhaustedException(String message) {
    super(message);
  }

  /** @return {@link OrderErrorCode#CHECKOUT_DISCOUNT_EXHAUSTED} */
  @Override
  public String errorCode() {
    return OrderErrorCode.CHECKOUT_DISCOUNT_EXHAUSTED.name();
  }
}
