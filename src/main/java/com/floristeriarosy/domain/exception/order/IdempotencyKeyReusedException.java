package com.floristeriarosy.domain.exception.order;

import com.floristeriarosy.domain.exception.HasErrorCode;
import com.floristeriarosy.domain.exception.UnprocessableException;

/** Same {@code Idempotency-Key}, different cart contents — treated as a client bug (ADR-011). */
public final class IdempotencyKeyReusedException extends UnprocessableException implements HasErrorCode {

  /**
   * @param message what the caller should see
   */
  public IdempotencyKeyReusedException(String message) {
    super(message);
  }

  /** @return {@link OrderErrorCode#IDEMPOTENCY_KEY_REUSED} */
  @Override
  public String errorCode() {
    return OrderErrorCode.IDEMPOTENCY_KEY_REUSED.name();
  }
}
