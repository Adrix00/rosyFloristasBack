package com.floristeriarosy.domain.exception.discount;

import com.floristeriarosy.domain.exception.ConflictException;
import com.floristeriarosy.domain.exception.HasErrorCode;

/** A {@code DELETE} was requested on a discount that has already started (product-discounts.md, section 3.4). */
public final class DiscountAlreadyStartedException extends ConflictException implements HasErrorCode {

  /**
   * @param message a message for a person; never exposed raw to the API client
   */
  public DiscountAlreadyStartedException(String message) {
    super(message);
  }

  @Override
  public String errorCode() {
    return DiscountErrorCode.DISCOUNT_ALREADY_STARTED.name();
  }
}
