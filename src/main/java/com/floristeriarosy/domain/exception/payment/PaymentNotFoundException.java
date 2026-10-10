package com.floristeriarosy.domain.exception.payment;

import com.floristeriarosy.domain.exception.HasErrorCode;
import com.floristeriarosy.domain.exception.NotFoundException;

/** The order has no payment associated (payment.md, §9). */
public final class PaymentNotFoundException extends NotFoundException implements HasErrorCode {

  /**
   * @param message what the caller should see
   */
  public PaymentNotFoundException(String message) {
    super(message);
  }

  /**
   * @return {@link PaymentErrorCode#PAYMENT_NOT_FOUND}
   */
  @Override
  public String errorCode() {
    return PaymentErrorCode.PAYMENT_NOT_FOUND.name();
  }
}
