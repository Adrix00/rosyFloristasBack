package com.floristeriarosy.domain.exception.payment;

import com.floristeriarosy.domain.exception.BadGatewayException;
import com.floristeriarosy.domain.exception.HasErrorCode;

/**
 * The payment gateway fails or refuses an operation for a reason that is not a business rejection
 * (payment.md, §9).
 */
public final class PaymentGatewayException extends BadGatewayException implements HasErrorCode {

  private static final String MESSAGE = "The payment gateway could not process the request";

  /** The message is fixed so the gateway's own text can never reach the API client. */
  public PaymentGatewayException() {
    super(MESSAGE);
  }

  /**
   * @return {@link PaymentErrorCode#PAYMENT_GATEWAY_ERROR}
   */
  @Override
  public String errorCode() {
    return PaymentErrorCode.PAYMENT_GATEWAY_ERROR.name();
  }
}
