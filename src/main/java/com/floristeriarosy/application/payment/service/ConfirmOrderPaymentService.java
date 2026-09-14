package com.floristeriarosy.application.payment.service;

import com.floristeriarosy.application.payment.port.in.ConfirmOrderPaymentUseCase;
import com.floristeriarosy.application.payment.port.out.PaymentWritePort;
import com.floristeriarosy.domain.model.order.valueobject.OrderId;
import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;
import com.floristeriarosy.domain.model.payment.Payment;
import com.floristeriarosy.domain.model.payment.PaymentMethod;
import com.floristeriarosy.domain.model.payment.PaymentProvider;
import com.floristeriarosy.domain.model.payment.valueobject.PaymentId;
import java.math.BigDecimal;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Implements {@link ConfirmOrderPaymentUseCase}: Redsys already approved this charge (the
 * notification is the proof) — this just records it. Never called on decline (order.md §1: an
 * unpaid order leaves no trace, nothing to record).
 */
@Service
public class ConfirmOrderPaymentService implements ConfirmOrderPaymentUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(ConfirmOrderPaymentService.class);

  private final PaymentWritePort writePort;

  /**
   * @param writePort persists the captured payment
   */
  public ConfirmOrderPaymentService(PaymentWritePort writePort) {
    this.writePort = writePort;
  }

  /**
   * @param orderId the order being confirmed
   * @param orderNumber the business identifier, unused here but part of the contract (ADR-017's
   *     signature diversification already happened before this is called)
   * @param method how the customer paid
   * @param amount the confirmed amount
   * @param providerPaymentIntentId the gateway's authorization reference
   * @return the saved, captured payment
   */
  @Override
  public Payment execute(
      OrderId orderId,
      OrderNumber orderNumber,
      PaymentMethod method,
      BigDecimal amount,
      String providerPaymentIntentId) {
    LOGGER.debug("confirmOrderPayment orderId={} providerPaymentIntentId={}", orderId, providerPaymentIntentId);
    Payment payment = Payment.initiate(PaymentId.newId(), orderId, method, PaymentProvider.REDSYS, amount);
    payment.capture(providerPaymentIntentId, Instant.now());
    Payment result = writePort.save(payment);
    LOGGER.debug("confirmOrderPayment orderId={} -> saved", orderId);
    return result;
  }
}
