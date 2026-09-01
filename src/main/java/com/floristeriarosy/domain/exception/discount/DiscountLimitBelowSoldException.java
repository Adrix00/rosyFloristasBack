package com.floristeriarosy.domain.exception.discount;

import com.floristeriarosy.domain.exception.HasErrorCode;
import com.floristeriarosy.domain.exception.UnprocessableException;

/** The requested {@code quantityLimit} is lower than {@code quantitySold} ({@code chk_product_discounts_sold}). */
public final class DiscountLimitBelowSoldException extends UnprocessableException implements HasErrorCode {

  /**
   * @param message a message for a person; never exposed raw to the API client
   */
  public DiscountLimitBelowSoldException(String message) {
    super(message);
  }

  @Override
  public String errorCode() {
    return DiscountErrorCode.DISCOUNT_LIMIT_BELOW_SOLD.name();
  }
}
