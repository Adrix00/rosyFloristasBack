package com.floristeriarosy.domain.exception.category;

import com.floristeriarosy.domain.exception.HasErrorCode;
import com.floristeriarosy.domain.exception.NotFoundException;

/** The category identifier does not exist, or is {@code INACTIVE} on a public access. */
public final class CategoryNotFoundException extends NotFoundException implements HasErrorCode {

  public CategoryNotFoundException(String message) {
    super(message);
  }

  @Override
  public String errorCode() {
    return CategoryErrorCode.CATEGORY_NOT_FOUND.name();
  }
}
