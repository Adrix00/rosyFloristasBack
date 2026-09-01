package com.floristeriarosy.domain.exception.product;

import com.floristeriarosy.domain.exception.ConflictException;
import com.floristeriarosy.domain.exception.HasErrorCode;

/** {@code price} was changed while a discount is currently active (product.md, section 3.8). */
public final class ProductHasActiveDiscountException extends ConflictException
    implements HasErrorCode {

  /**
   * @param message a message for a person; never exposed raw to the API client
   */
  public ProductHasActiveDiscountException(String message) {
    super(message);
  }

  @Override
  public String errorCode() {
    return ProductErrorCode.PRODUCT_HAS_ACTIVE_DISCOUNT.name();
  }
}
