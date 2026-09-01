package com.floristeriarosy.domain.exception.product;

import com.floristeriarosy.domain.exception.ConflictException;
import com.floristeriarosy.domain.exception.HasErrorCode;

/** {@code DELETE} was attempted on a product with sales, stock movements or purchases (product.md, section 3.10). */
public final class ProductHasHistoryException extends ConflictException implements HasErrorCode {

  /**
   * @param message a message for a person; never exposed raw to the API client
   */
  public ProductHasHistoryException(String message) {
    super(message);
  }

  @Override
  public String errorCode() {
    return ProductErrorCode.PRODUCT_HAS_HISTORY.name();
  }
}
