package com.floristeriarosy.domain.exception.discount;

import com.floristeriarosy.domain.exception.HasErrorCode;
import com.floristeriarosy.domain.exception.UnprocessableException;

/**
 * A field was sent with a value different from its current one, but is not editable in the
 * discount's current state (product-discounts.md, section 3.3).
 */
public final class DiscountNotEditableException extends UnprocessableException implements HasErrorCode {

  /**
   * @param message a message for a person; never exposed raw to the API client
   */
  public DiscountNotEditableException(String message) {
    super(message);
  }

  @Override
  public String errorCode() {
    return DiscountErrorCode.DISCOUNT_NOT_EDITABLE.name();
  }
}
