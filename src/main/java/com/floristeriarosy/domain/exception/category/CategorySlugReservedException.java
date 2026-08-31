package com.floristeriarosy.domain.exception.category;

import com.floristeriarosy.domain.exception.HasErrorCode;
import com.floristeriarosy.domain.exception.UnprocessableException;

/** The name generates a slug reserved by a literal route segment (e.g. {@code all}). */
public final class CategorySlugReservedException extends UnprocessableException
    implements HasErrorCode {

  public CategorySlugReservedException(String message) {
    super(message);
  }

  @Override
  public String errorCode() {
    return CategoryErrorCode.CATEGORY_SLUG_RESERVED.name();
  }
}
