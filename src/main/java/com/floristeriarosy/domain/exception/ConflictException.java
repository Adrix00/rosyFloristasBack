package com.floristeriarosy.domain.exception;

/** The request conflicts with the resource's current state. Mapped to HTTP 409. */
public abstract class ConflictException extends RuntimeException {

  protected ConflictException(String message) {
    super(message);
  }
}
