package com.floristeriarosy.domain.exception.discount;

/** Business error codes published by the discount module (ADR-012). */
public enum DiscountErrorCode {
  DISCOUNT_NOT_FOUND,
  DISCOUNT_OVERLAP,
  DISCOUNT_PRICE_NOT_LOWER,
  DISCOUNT_PERIOD_INVALID,
  DISCOUNT_LIMIT_BELOW_SOLD,
  DISCOUNT_NOT_EDITABLE,
  DISCOUNT_ALREADY_STARTED,
  DISCOUNT_EXHAUSTED
}
