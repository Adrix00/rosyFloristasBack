package com.floristeriarosy.application.payment.port.in;

import com.floristeriarosy.application.payment.command.RefundOrderPaymentCommand;
import com.floristeriarosy.domain.model.order.valueobject.OrderId;
import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;
import com.floristeriarosy.domain.model.payment.Payment;

/** Implements {@code OrderPaymentPort.refund} (payment.md, section 7). */
public interface RefundOrderPaymentUseCase {

  /**
   * @param command which order's payment to refund
   * @return the refunded payment
   */
  Payment execute(RefundOrderPaymentCommand command);

  /**
   * Convenience overload for callers that must not depend on {@link RefundOrderPaymentCommand} —
   * e.g. {@code OrderPaymentAdapter}, an {@code infrastructure.persistence} class barred from the
   * {@code application..command} package (InfrastructureArchitectureTest: {@code
   * persistence_should_not_depend_on_commands}). Delegates to {@link #execute(RefundOrderPaymentCommand)}.
   *
   * @param orderId the order whose captured payment must be refunded in full
   * @param orderNumber the original charge's business identifier, needed by Redsys for refund
   *     signature diversification (ADR-017)
   * @return the refunded payment
   */
  default Payment execute(OrderId orderId, OrderNumber orderNumber) {
    return execute(new RefundOrderPaymentCommand(orderId, orderNumber));
  }
}
