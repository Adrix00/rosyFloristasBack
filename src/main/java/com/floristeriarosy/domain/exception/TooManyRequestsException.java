package com.floristeriarosy.domain.exception;

/**
 * A rate limit was exceeded (ADR-016). Mapped to HTTP 429, with a {@code Retry-After} header
 * computed from {@link #retryAfterSeconds()}.
 */
public abstract class TooManyRequestsException extends RuntimeException {

  private final long retryAfterSeconds;

  /**
   * @param message a message for a person; never exposed raw to the API client
   * @param retryAfterSeconds seconds until the caller should retry
   */
  protected TooManyRequestsException(String message, long retryAfterSeconds) {
    super(message);
    this.retryAfterSeconds = retryAfterSeconds;
  }

  /**
   * @return seconds until the caller should retry, for the {@code Retry-After} header
   */
  public long retryAfterSeconds() {
    return retryAfterSeconds;
  }
}
