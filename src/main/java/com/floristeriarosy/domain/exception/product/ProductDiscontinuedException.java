package com.floristeriarosy.domain.exception.product;

import com.floristeriarosy.domain.exception.ConflictException;
import com.floristeriarosy.domain.exception.HasErrorCode;

/** A {@code DISCONTINUED} product was edited, or an attempt was made to take it out of that terminal state. */
public final class ProductDiscontinuedException extends ConflictException implements HasErrorCode {

  /**
   * @param message a message for a person; never exposed raw to the API client
   */
  public ProductDiscontinuedException(String message) {
    super(message);
  }

  @Override
  public String errorCode() {
    return ProductErrorCode.PRODUCT_DISCONTINUED.name();
  }
}
