package com.floristeriarosy.infrastructure.persistence.adapter.order;

import com.floristeriarosy.application.order.port.out.ChargeCommand;
import com.floristeriarosy.application.order.port.out.ChargeResult;
import com.floristeriarosy.application.order.port.out.OrderPaymentPort;
import com.floristeriarosy.application.order.port.out.RefundCommand;
import com.floristeriarosy.application.payment.command.ChargeOrderPaymentCommand;
import com.floristeriarosy.application.payment.command.RefundOrderPaymentCommand;
import com.floristeriarosy.application.payment.port.in.ChargeOrderPaymentUseCase;
import com.floristeriarosy.application.payment.port.in.RefundOrderPaymentUseCase;
import com.floristeriarosy.domain.model.payment.Payment;
import org.springframework.stereotype.Component;

/**
 * Implements {@link OrderPaymentPort} (ADR-003) by delegating to {@code payment}'s own use cases —
 * same pattern as {@code ProductInventoryPersistenceAdapter} delegating to {@code inventory}'s
 * {@code RegisterStockMovementUseCase}. Carries no logic of its own beyond translating between
 * {@code order}'s command/result shapes and {@code payment}'s.
 */
@Component
public class OrderPaymentAdapter implements OrderPaymentPort {

  private final ChargeOrderPaymentUseCase chargeUseCase;
  private final RefundOrderPaymentUseCase refundUseCase;

  /**
   * @param chargeUseCase payment's real charging logic
   * @param refundUseCase payment's real refunding logic
   */
  public OrderPaymentAdapter(ChargeOrderPaymentUseCase chargeUseCase, RefundOrderPaymentUseCase refundUseCase) {
    this.chargeUseCase = chargeUseCase;
    this.refundUseCase = refundUseCase;
  }

  /**
   * @param command what to charge and how
   * @return a redirect to complete payment, or an already-settled charge
   */
  @Override
  public ChargeResult charge(ChargeCommand command) {
    return chargeUseCase.execute(
        new ChargeOrderPaymentCommand(
            command.orderId(), command.orderNumber(), command.amount(), command.method(),
            command.paymentMethodToken(), command.idempotencyKey()));
  }

  /**
   * @param command which order's payment to refund
   * @return the refunded payment
   */
  @Override
  public Payment refund(RefundCommand command) {
    return refundUseCase.execute(new RefundOrderPaymentCommand(command.orderId(), command.orderNumber()));
  }
}
