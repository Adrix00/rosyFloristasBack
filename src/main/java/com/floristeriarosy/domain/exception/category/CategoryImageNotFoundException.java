package com.floristeriarosy.domain.exception.category;

import com.floristeriarosy.domain.exception.HasErrorCode;
import com.floristeriarosy.domain.exception.UnprocessableException;

/** {@code imageId} does not reference an existing row in {@code images}. */
public final class CategoryImageNotFoundException extends UnprocessableException
    implements HasErrorCode {

  /**
   * @param message a message for a person; never exposed raw to the API client
   */
  public CategoryImageNotFoundException(String message) {
    super(message);
  }

  @Override
  public String errorCode() {
    return CategoryErrorCode.CATEGORY_IMAGE_NOT_FOUND.name();
  }
}
