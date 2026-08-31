package com.floristeriarosy.domain.exception;

/** A requested resource does not exist. Mapped to HTTP 404 by infrastructure/web/advice. */
public abstract class NotFoundException extends RuntimeException {

  /**
   * @param message a message for a person; never exposed raw to the API client
   */
  protected NotFoundException(String message) {
    super(message);
  }
}
