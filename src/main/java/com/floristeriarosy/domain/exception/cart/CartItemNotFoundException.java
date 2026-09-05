package com.floristeriarosy.domain.exception.cart;

import com.floristeriarosy.domain.exception.HasErrorCode;
import com.floristeriarosy.domain.exception.NotFoundException;

/** {@code PATCH}/{@code DELETE} targets a product with no line in the cart (cart.md, section 9). */
public final class CartItemNotFoundException extends NotFoundException implements HasErrorCode {

  /**
   * @param message a message for a person; never exposed raw to the API client
   */
  public CartItemNotFoundException(String message) {
    super(message);
  }

  @Override
  public String errorCode() {
    return CartErrorCode.CART_ITEM_NOT_FOUND.name();
  }
}
