package com.floristeriarosy.domain.exception.order;

import com.floristeriarosy.domain.exception.ConflictException;
import com.floristeriarosy.domain.exception.HasErrorCode;

/**
 * An admin action does not apply to the order's current status or fulfillment mode (order.md, §9)
 * — e.g. {@code ship} on a {@code PICKUP} order, or {@code reject} on a {@code PREPARING} one.
 */
public final class OrderInvalidTransitionException extends ConflictException implements HasErrorCode {

  /**
   * @param message what the caller should see
   */
  public OrderInvalidTransitionException(String message) {
    super(message);
  }

  /**
   * @return {@link OrderErrorCode#ORDER_INVALID_TRANSITION}
   */
  @Override
  public String errorCode() {
    return OrderErrorCode.ORDER_INVALID_TRANSITION.name();
  }
}
