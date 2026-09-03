package com.floristeriarosy.domain.exception.auth;

/** Thrown when a customer attempts to login but their email is not verified. */
public class EmailNotVerifiedException extends RuntimeException {
  public EmailNotVerifiedException(String message) {
    super(message);
  }
}
