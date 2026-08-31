package com.floristeriarosy.domain.exception;

/** A requested resource does not exist. Mapped to HTTP 404 by infrastructure/web/advice. */
public abstract class NotFoundException extends RuntimeException {

  protected NotFoundException(String message) {
    super(message);
  }
}
