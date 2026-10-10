package com.floristeriarosy.application.payment.port.out;

import com.floristeriarosy.domain.model.payment.Payment;

/** Write access to {@code payments} (payment.md, section 8). */
public interface PaymentWritePort {

  /**
   * @param payment the payment to insert or update
   * @return the saved payment, with timestamps populated by the database
   */
  Payment save(Payment payment);
}
