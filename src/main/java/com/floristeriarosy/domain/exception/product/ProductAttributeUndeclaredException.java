package com.floristeriarosy.domain.exception.product;

import com.floristeriarosy.domain.exception.HasErrorCode;
import com.floristeriarosy.domain.exception.UnprocessableException;

/** A key in {@code attributes} is not declared in {@code product_attribute_definitions}. */
public final class ProductAttributeUndeclaredException extends UnprocessableException
    implements HasErrorCode {

  /**
   * @param message a message for a person; never exposed raw to the API client
   */
  public ProductAttributeUndeclaredException(String message) {
    super(message);
  }

  @Override
  public String errorCode() {
    return ProductErrorCode.PRODUCT_ATTRIBUTE_UNDECLARED.name();
  }
}
