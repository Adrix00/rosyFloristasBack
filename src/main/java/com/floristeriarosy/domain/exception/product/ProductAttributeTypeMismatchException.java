package com.floristeriarosy.domain.exception.product;

import com.floristeriarosy.domain.exception.HasErrorCode;
import com.floristeriarosy.domain.exception.UnprocessableException;

/** A value in {@code attributes} does not respect its key's declared {@code data_type}. */
public final class ProductAttributeTypeMismatchException extends UnprocessableException
    implements HasErrorCode {

  /**
   * @param message a message for a person; never exposed raw to the API client
   */
  public ProductAttributeTypeMismatchException(String message) {
    super(message);
  }

  @Override
  public String errorCode() {
    return ProductErrorCode.PRODUCT_ATTRIBUTE_TYPE_MISMATCH.name();
  }
}
