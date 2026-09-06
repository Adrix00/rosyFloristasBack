package com.floristeriarosy.domain.exception.cart;

import com.floristeriarosy.domain.exception.HasErrorCode;
import com.floristeriarosy.domain.exception.UnprocessableException;

/** A cart would exceed {@link com.floristeriarosy.domain.model.cart.Cart#MAX_LINES}. */
public final class CartLineLimitExceededException extends UnprocessableException
    implements HasErrorCode {

  /**
   * @param message a message for a person; never exposed raw to the API client
   */
  public CartLineLimitExceededException(String message) {
    super(message);
  }

  @Override
  public String errorCode() {
    return CartErrorCode.CART_LINE_LIMIT_EXCEEDED.name();
  }
}
