package com.floristeriarosy.domain.exception.category;

import com.floristeriarosy.domain.exception.ConflictException;
import com.floristeriarosy.domain.exception.HasErrorCode;

/** The slug generated from the category name is already in use. */
public final class CategoryAlreadyExistsException extends ConflictException
    implements HasErrorCode {

  public CategoryAlreadyExistsException(String message) {
    super(message);
  }

  @Override
  public String errorCode() {
    return CategoryErrorCode.CATEGORY_ALREADY_EXISTS.name();
  }
}
