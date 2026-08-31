package com.floristeriarosy.domain.exception.category;

import com.floristeriarosy.domain.exception.HasErrorCode;
import com.floristeriarosy.domain.exception.UnprocessableException;

/** {@code imageId} does not reference an existing row in {@code images}. */
public final class CategoryImageNotFoundException extends UnprocessableException
    implements HasErrorCode {

  public CategoryImageNotFoundException(String message) {
    super(message);
  }

  @Override
  public String errorCode() {
    return CategoryErrorCode.CATEGORY_IMAGE_NOT_FOUND.name();
  }
}
