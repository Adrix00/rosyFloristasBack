package com.floristeriarosy.domain.exception.product;

import com.floristeriarosy.domain.exception.HasErrorCode;
import com.floristeriarosy.domain.exception.UnprocessableException;

/** A product is being created without any category; it would be born invisible (product.md, section 3.4). */
public final class ProductWithoutCategoryException extends UnprocessableException
    implements HasErrorCode {

  /**
   * @param message a message for a person; never exposed raw to the API client
   */
  public ProductWithoutCategoryException(String message) {
    super(message);
  }

  @Override
  public String errorCode() {
    return ProductErrorCode.PRODUCT_WITHOUT_CATEGORY.name();
  }
}
