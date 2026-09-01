package com.floristeriarosy.domain.exception;

/**
 * A concurrent write changed the aggregate between read and write (ADR-009: optimistic locking).
 * Shared across every {@code @Version}-carrying aggregate ({@code products}, {@code orders},
 * {@code customers}, ...) — the client must re-read and decide, the server never retries silently.
 */
public final class ResourceModifiedException extends ConflictException implements HasErrorCode {

  /**
   * @param message a message for a person; never exposed raw to the API client
   */
  public ResourceModifiedException(String message) {
    super(message);
  }

  @Override
  public String errorCode() {
    return "RESOURCE_MODIFIED";
  }
}
