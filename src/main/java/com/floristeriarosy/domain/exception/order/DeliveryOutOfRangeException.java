package com.floristeriarosy.domain.exception.order;

import com.floristeriarosy.domain.exception.HasErrorCode;
import com.floristeriarosy.domain.exception.UnprocessableException;

/**
 * The delivery address is more than 10 km from the store — no shipping band applies (order.md, §3.7,
 * §9).
 */
public final class DeliveryOutOfRangeException extends UnprocessableException implements HasErrorCode {

  /**
   * @param message what the caller should see
   */
  public DeliveryOutOfRangeException(String message) {
    super(message);
  }

  /** @return {@link OrderErrorCode#DELIVERY_OUT_OF_RANGE} */
  @Override
  public String errorCode() {
    return OrderErrorCode.DELIVERY_OUT_OF_RANGE.name();
  }
}
