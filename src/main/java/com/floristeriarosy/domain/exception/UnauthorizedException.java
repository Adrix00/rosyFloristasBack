package com.floristeriarosy.domain.exception;

/**
 * The request is well-formed but the credentials it carries are wrong (e.g. a current password
 * that does not match). Mapped to HTTP 401. Distinct from an authentication-missing 401, which
 * never reaches a domain exception at all — that is a filter-level concern (ADR-008).
 */
public abstract class UnauthorizedException extends RuntimeException {

  /**
   * @param message a message for a person; never exposed raw to the API client
   */
  protected UnauthorizedException(String message) {
    super(message);
  }
}
