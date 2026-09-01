package com.floristeriarosy.domain.exception.discount;

import com.floristeriarosy.domain.exception.HasErrorCode;
import com.floristeriarosy.domain.exception.UnprocessableException;

/** {@code salePrice} is not strictly lower than the price it is being compared against. */
public final class DiscountPriceNotLowerException extends UnprocessableException implements HasErrorCode {

  /**
   * @param message a message for a person; never exposed raw to the API client
   */
  public DiscountPriceNotLowerException(String message) {
    super(message);
  }

  @Override
  public String errorCode() {
    return DiscountErrorCode.DISCOUNT_PRICE_NOT_LOWER.name();
  }
}
