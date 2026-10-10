package com.floristeriarosy.domain.exception.order;

import com.floristeriarosy.domain.exception.HasErrorCode;
import com.floristeriarosy.domain.exception.NotFoundException;

/**
 * {@code GET /orders/{orderNumber}}: no order with that number, or the caller does not own it
 * (order.md, §9).
 */
public final class OrderNotFoundException extends NotFoundException implements HasErrorCode {

  /**
   * @param message what the caller should see
   */
  public OrderNotFoundException(String message) {
    super(message);
  }

  /** @return {@link OrderErrorCode#ORDER_NOT_FOUND} */
  @Override
  public String errorCode() {
    return OrderErrorCode.ORDER_NOT_FOUND.name();
  }
}
