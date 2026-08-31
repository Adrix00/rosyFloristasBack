package com.floristeriarosy.domain.exception.category;

import com.floristeriarosy.domain.exception.HasErrorCode;
import com.floristeriarosy.domain.exception.UnprocessableException;

/** The reorder request does not include every existing category. */
public final class CategoryPositionsIncompleteException extends UnprocessableException
    implements HasErrorCode {

  public CategoryPositionsIncompleteException(String message) {
    super(message);
  }

  @Override
  public String errorCode() {
    return CategoryErrorCode.CATEGORY_POSITIONS_INCOMPLETE.name();
  }
}
