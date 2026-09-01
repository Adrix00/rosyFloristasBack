package com.floristeriarosy.domain.exception.product;

import com.floristeriarosy.domain.exception.HasErrorCode;
import com.floristeriarosy.domain.exception.UnprocessableException;

/** Inventory management was enabled without an initial stock value. */
public final class ProductStockRequiredException extends UnprocessableException
    implements HasErrorCode {

  /**
   * @param message a message for a person; never exposed raw to the API client
   */
  public ProductStockRequiredException(String message) {
    super(message);
  }

  @Override
  public String errorCode() {
    return ProductErrorCode.PRODUCT_STOCK_REQUIRED.name();
  }
}
