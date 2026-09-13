package com.floristeriarosy.application.payment.port.in;

import com.floristeriarosy.application.order.port.out.ChargeResult;
import com.floristeriarosy.application.payment.command.ChargeOrderPaymentCommand;

/** Implements {@code OrderPaymentPort.charge} (payment.md, section 7). */
public interface ChargeOrderPaymentUseCase {

  /**
   * @param command what to charge and how
   * @return a redirect to complete payment, or an already-settled charge
   */
  ChargeResult execute(ChargeOrderPaymentCommand command);
}
