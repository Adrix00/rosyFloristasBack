package com.floristeriarosy.domain.exception.cart;

import com.floristeriarosy.domain.exception.HasErrorCode;
import com.floristeriarosy.domain.exception.NotFoundException;

/** The product does not exist, or is not visible in the storefront sense (cart.md, rule 3.6). */
public final class CartProductNotFoundException extends NotFoundException implements HasErrorCode {

  /**
   * @param message a message for a person; never exposed raw to the API client
   */
  public CartProductNotFoundException(String message) {
    super(message);
  }

  @Override
  public String errorCode() {
    return CartErrorCode.CART_PRODUCT_NOT_FOUND.name();
  }
}
