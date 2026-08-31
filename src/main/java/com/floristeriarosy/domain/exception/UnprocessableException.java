package com.floristeriarosy.domain.exception;

/** A business rule rejects the request even though it is well-formed. Mapped to HTTP 422. */
public abstract class UnprocessableException extends RuntimeException {

  protected UnprocessableException(String message) {
    super(message);
  }
}
