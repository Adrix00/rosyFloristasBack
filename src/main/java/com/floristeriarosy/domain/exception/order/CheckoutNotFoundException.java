package com.floristeriarosy.domain.exception.order;

import com.floristeriarosy.domain.exception.HasErrorCode;
import com.floristeriarosy.domain.exception.NotFoundException;

/**
 * {@code GET /checkout/{idempotencyKey}/status}: no checkout attempt with that key (order.md, §9).
 */
public final class CheckoutNotFoundException extends NotFoundException implements HasErrorCode {

  /**
   * @param message what the caller should see
   */
  public CheckoutNotFoundException(String message) {
    super(message);
  }

  /** @return {@link OrderErrorCode#CHECKOUT_NOT_FOUND} */
  @Override
  public String errorCode() {
    return OrderErrorCode.CHECKOUT_NOT_FOUND.name();
  }
}
