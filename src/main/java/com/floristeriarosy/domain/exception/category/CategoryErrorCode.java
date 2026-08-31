package com.floristeriarosy.domain.exception.category;

/** Business error codes published by the category module (ADR-012). */
public enum CategoryErrorCode {
  CATEGORY_NOT_FOUND,
  CATEGORY_ALREADY_EXISTS,
  CATEGORY_VALIDATION_FAILED,
  CATEGORY_POSITIONS_INCOMPLETE,
  CATEGORY_IMAGE_NOT_FOUND,
  CATEGORY_SLUG_RESERVED
}
