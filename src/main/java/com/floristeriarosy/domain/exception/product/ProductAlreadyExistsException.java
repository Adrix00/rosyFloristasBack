package com.floristeriarosy.domain.exception.product;

import com.floristeriarosy.domain.exception.ConflictException;
import com.floristeriarosy.domain.exception.HasErrorCode;

/** The slug generated from the product name is already in use. */
public final class ProductAlreadyExistsException extends ConflictException implements HasErrorCode {

  /**
   * @param message a message for a person; never exposed raw to the API client
   */
  public ProductAlreadyExistsException(String message) {
    super(message);
  }

  @Override
  public String errorCode() {
    return ProductErrorCode.PRODUCT_ALREADY_EXISTS.name();
  }
}
