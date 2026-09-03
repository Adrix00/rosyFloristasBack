package com.floristeriarosy.domain.exception;

/**
 * The identifier or IP bucket for this endpoint is exhausted (ADR-016). Not module-specific —
 * shared across every rate-limited endpoint the same way {@link ResourceModifiedException} is
 * shared across every optimistically-locked aggregate.
 */
public final class RateLimitExceededException extends TooManyRequestsException
    implements HasErrorCode {

  /**
   * @param message a message for a person; never exposed raw to the API client
   * @param retryAfterSeconds seconds until the bucket refills enough to retry
   */
  public RateLimitExceededException(String message, long retryAfterSeconds) {
    super(message, retryAfterSeconds);
  }

  @Override
  public String errorCode() {
    return "RATE_LIMIT_EXCEEDED";
  }
}
