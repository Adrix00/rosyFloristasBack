package com.floristeriarosy.domain.exception.order;

import com.floristeriarosy.domain.exception.HasErrorCode;
import com.floristeriarosy.domain.exception.UnprocessableException;

/** {@code CheckoutRequest}'s cross-field business rule failed (order.md, §5, §9). */
public final class OrderValidationFailedException extends UnprocessableException implements HasErrorCode {

  /**
   * @param message what the caller should see
   */
  public OrderValidationFailedException(String message) {
    super(message);
  }

  /** @return {@link OrderErrorCode#ORDER_VALIDATION_FAILED} */
  @Override
  public String errorCode() {
    return OrderErrorCode.ORDER_VALIDATION_FAILED.name();
  }
}
