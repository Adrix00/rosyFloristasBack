package com.floristeriarosy.domain.exception.product;

import com.floristeriarosy.domain.exception.HasErrorCode;
import com.floristeriarosy.domain.exception.UnprocessableException;

/**
 * A product was suggested as its own extra; {@code chk_product_suggestions_not_self} exists
 * precisely to forbid this (product.md, section 10).
 */
public final class ProductSuggestsItselfException extends UnprocessableException implements HasErrorCode {

  /**
   * @param message a message for a person; never exposed raw to the API client
   */
  public ProductSuggestsItselfException(String message) {
    super(message);
  }

  @Override
  public String errorCode() {
    return ProductErrorCode.PRODUCT_SUGGESTS_ITSELF.name();
  }
}
