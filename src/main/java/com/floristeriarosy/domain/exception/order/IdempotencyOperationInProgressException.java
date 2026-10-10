package com.floristeriarosy.domain.exception.order;

import com.floristeriarosy.domain.exception.ConflictException;

/** A concurrent request already holds this {@code Idempotency-Key} (ADR-011). */
public final class IdempotencyOperationInProgressException extends ConflictException {

  /**
   * @param message what the caller should see
   */
  public IdempotencyOperationInProgressException(String message) {
    super(message);
  }
}
