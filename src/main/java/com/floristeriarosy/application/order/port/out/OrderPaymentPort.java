package com.floristeriarosy.application.order.port.out;

import com.floristeriarosy.domain.model.payment.Payment;

/**
 * The frontier between {@code order} and payment capture (order.md, section 8; payment.md, section
 * 1): {@code order} asks "charge this" and "refund this" without knowing whether Redsys, cash, or
 * any other provider is behind it — same principle as {@code ProductInventoryPort} facing {@code
 * inventory}.
 */
public interface OrderPaymentPort {

  /**
   * @param command what to charge and how
   * @return a redirect to complete payment, or an already-settled charge
   */
  ChargeResult charge(ChargeCommand command);

  /**
   * @param command which order's payment to refund
   * @return the refunded payment
   */
  Payment refund(RefundCommand command);
}
