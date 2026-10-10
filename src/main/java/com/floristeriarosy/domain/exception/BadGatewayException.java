package com.floristeriarosy.domain.exception;

/** An external service fails for a reason unrelated to the business request. Mapped to HTTP 502. */
public abstract class BadGatewayException extends RuntimeException {

  /**
   * @param message a message for a person; never exposed raw to the API client
   */
  protected BadGatewayException(String message) {
    super(message);
  }
}
