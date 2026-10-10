package com.floristeriarosy.application.payment.port.in;

import com.floristeriarosy.domain.model.order.valueobject.OrderId;
import com.floristeriarosy.domain.model.payment.Payment;
import com.floristeriarosy.domain.model.payment.PaymentMethod;
import java.math.BigDecimal;

/** Implements {@code OrderPaymentPort.confirmCharge} (ADR-017). */
public interface ConfirmOrderPaymentUseCase {

  /**
   * @param orderId the order being confirmed
   * @param method how the customer paid
   * @param amount the confirmed amount
   * @param providerPaymentIntentId the gateway's authorization reference
   * @return the saved, captured payment
   */
  Payment execute(OrderId orderId, PaymentMethod method, BigDecimal amount, String providerPaymentIntentId);
}
