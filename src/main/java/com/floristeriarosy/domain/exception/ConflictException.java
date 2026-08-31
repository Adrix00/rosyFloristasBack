package com.floristeriarosy.domain.exception;

/** The request conflicts with the resource's current state. Mapped to HTTP 409. */
public abstract class ConflictException extends RuntimeException {

  /**
   * @param message a message for a person; never exposed raw to the API client
   */
  protected ConflictException(String message) {
    super(message);
  }
}
