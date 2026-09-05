package com.floristeriarosy.domain.exception.cart;

import com.floristeriarosy.domain.exception.HasErrorCode;
import com.floristeriarosy.domain.exception.UnprocessableException;

/** A line would exceed {@link com.floristeriarosy.domain.model.cart.Cart#MAX_QUANTITY_PER_LINE}. */
public final class CartItemLimitExceededException extends UnprocessableException
    implements HasErrorCode {

  /**
   * @param message a message for a person; never exposed raw to the API client
   */
  public CartItemLimitExceededException(String message) {
    super(message);
  }

  @Override
  public String errorCode() {
    return CartErrorCode.CART_ITEM_LIMIT_EXCEEDED.name();
  }
}
