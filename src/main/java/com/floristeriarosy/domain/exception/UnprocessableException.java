package com.floristeriarosy.domain.exception;

/** A business rule rejects the request even though it is well-formed. Mapped to HTTP 422. */
public abstract class UnprocessableException extends RuntimeException {

  /**
   * @param message a message for a person; never exposed raw to the API client
   */
  protected UnprocessableException(String message) {
    super(message);
  }
}
