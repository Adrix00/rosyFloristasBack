package com.floristeriarosy.domain.exception.payment;

import com.floristeriarosy.domain.exception.BadGatewayException;
import com.floristeriarosy.domain.exception.HasErrorCode;

/**
 * The payment gateway fails or refuses an operation for a reason that is not a business rejection
 * (payment.md, §9).
 */
public final class PaymentGatewayException extends BadGatewayException implements HasErrorCode {

  /**
   * @param message what the caller should see; the gateway's own reason stays in the server log
   */
  public PaymentGatewayException(String message) {
    super(message);
  }

  /**
   * @return {@link PaymentErrorCode#PAYMENT_GATEWAY_ERROR}
   */
  @Override
  public String errorCode() {
    return PaymentErrorCode.PAYMENT_GATEWAY_ERROR.name();
  }
}
