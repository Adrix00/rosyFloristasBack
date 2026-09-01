package com.floristeriarosy.domain.exception.discount;

import com.floristeriarosy.domain.exception.ConflictException;
import com.floristeriarosy.domain.exception.HasErrorCode;

/**
 * A discount's vigency window overlaps another discount of the same product ({@code
 * ex_product_discounts_no_overlap}, product-discounts.md, section 2 and 9).
 */
public final class DiscountOverlapException extends ConflictException implements HasErrorCode {

  /**
   * @param message a message for a person; never exposed raw to the API client
   */
  public DiscountOverlapException(String message) {
    super(message);
  }

  @Override
  public String errorCode() {
    return DiscountErrorCode.DISCOUNT_OVERLAP.name();
  }
}
