package com.floristeriarosy.domain.exception.category;

public final class CategoryInUseException extends RuntimeException {

  public CategoryInUseException(String message) {
    super(message);
  }
}
