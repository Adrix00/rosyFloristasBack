package com.floristeriarosy.domain.exception.product;

import com.floristeriarosy.domain.exception.HasErrorCode;
import com.floristeriarosy.domain.exception.UnprocessableException;

/** The name normalizes to an empty slug (e.g. {@code "---"} strips down to nothing). */
public final class ProductSlugEmptyException extends UnprocessableException implements HasErrorCode {

  /**
   * @param message a message for a person; never exposed raw to the API client
   */
  public ProductSlugEmptyException(String message) {
    super(message);
  }

  @Override
  public String errorCode() {
    return ProductErrorCode.PRODUCT_SLUG_EMPTY.name();
  }
}
