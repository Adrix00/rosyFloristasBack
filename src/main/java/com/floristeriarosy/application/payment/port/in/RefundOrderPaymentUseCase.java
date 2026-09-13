package com.floristeriarosy.application.payment.port.in;

import com.floristeriarosy.application.payment.command.RefundOrderPaymentCommand;
import com.floristeriarosy.domain.model.payment.Payment;

/** Implements {@code OrderPaymentPort.refund} (payment.md, section 7). */
public interface RefundOrderPaymentUseCase {

  /**
   * @param command which order's payment to refund
   * @return the refunded payment
   */
  Payment execute(RefundOrderPaymentCommand command);
}
