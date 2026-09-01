package com.floristeriarosy.domain.exception.discount;

import com.floristeriarosy.domain.exception.HasErrorCode;
import com.floristeriarosy.domain.exception.NotFoundException;

/** No discount exists with the requested id (product-discounts.md, section 9). */
public final class DiscountNotFoundException extends NotFoundException implements HasErrorCode {

  /**
   * @param message a message for a person; never exposed raw to the API client
   */
  public DiscountNotFoundException(String message) {
    super(message);
  }

  @Override
  public String errorCode() {
    return DiscountErrorCode.DISCOUNT_NOT_FOUND.name();
  }
}
