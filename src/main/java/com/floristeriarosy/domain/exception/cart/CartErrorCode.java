package com.floristeriarosy.domain.exception.cart;

/** Business error codes published by the cart module (ADR-012, cart.md §9). */
public enum CartErrorCode {
  CART_PRODUCT_NOT_FOUND,
  CART_ITEM_NOT_FOUND,
  CART_INSUFFICIENT_STOCK,
  CART_ITEM_LIMIT_EXCEEDED,
  CART_LINE_LIMIT_EXCEEDED,
  CART_VALIDATION_FAILED
}
