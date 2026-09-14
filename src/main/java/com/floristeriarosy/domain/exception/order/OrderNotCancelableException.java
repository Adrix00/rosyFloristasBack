package com.floristeriarosy.domain.exception.order;

import com.floristeriarosy.domain.exception.ConflictException;
import com.floristeriarosy.domain.exception.HasErrorCode;

/**
 * A customer tried to cancel their own order outside {@code PENDING} (order.md, §9) — the
 * cancel-specific, friendlier sibling of {@link OrderInvalidTransitionException}, which an admin
 * gets instead for the same order in the same state.
 */
public final class OrderNotCancelableException extends ConflictException implements HasErrorCode {

  /**
   * @param message what the caller should see
   */
  public OrderNotCancelableException(String message) {
    super(message);
  }

  /**
   * @return {@link OrderErrorCode#ORDER_NOT_CANCELABLE}
   */
  @Override
  public String errorCode() {
    return OrderErrorCode.ORDER_NOT_CANCELABLE.name();
  }
}
