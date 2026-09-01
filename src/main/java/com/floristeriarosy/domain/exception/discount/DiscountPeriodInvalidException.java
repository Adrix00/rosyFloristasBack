package com.floristeriarosy.domain.exception.discount;

import com.floristeriarosy.domain.exception.HasErrorCode;
import com.floristeriarosy.domain.exception.UnprocessableException;

/** {@code endsAt} is not strictly after {@code startsAt}, or is not in the future. */
public final class DiscountPeriodInvalidException extends UnprocessableException implements HasErrorCode {

  /**
   * @param message a message for a person; never exposed raw to the API client
   */
  public DiscountPeriodInvalidException(String message) {
    super(message);
  }

  @Override
  public String errorCode() {
    return DiscountErrorCode.DISCOUNT_PERIOD_INVALID.name();
  }
}
