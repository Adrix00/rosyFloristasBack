package com.floristeriarosy.infrastructure.persistence.adapter.order;

import com.floristeriarosy.application.order.dto.ChargeResult;
import com.floristeriarosy.application.order.port.out.OrderPaymentPort;
import com.floristeriarosy.application.payment.port.in.ChargeOrderPaymentUseCase;
import com.floristeriarosy.application.payment.port.in.ConfirmOrderPaymentUseCase;
import com.floristeriarosy.application.payment.port.in.RefundOrderPaymentUseCase;
import com.floristeriarosy.domain.model.order.valueobject.OrderId;
import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;
import com.floristeriarosy.domain.model.payment.Payment;
import com.floristeriarosy.domain.model.payment.PaymentMethod;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * Implements {@link OrderPaymentPort} (ADR-003) by delegating to {@code payment}'s own use cases —
 * same pattern as {@code ProductInventoryPersistenceAdapter} delegating to {@code inventory}'s
 * {@code RegisterStockMovementUseCase}. Carries no logic of its own beyond translating between
 * {@code order}'s port/result shapes and {@code payment}'s.
 *
 * <p>Uses the primitive-parameter overloads of the use cases so this persistence adapter never
 * imports anything from {@code application..command}, satisfying ArchUnit's
 * {@code persistence_should_not_depend_on_commands}.
 */
@Component
public class OrderPaymentAdapter implements OrderPaymentPort {

  private final ChargeOrderPaymentUseCase chargeUseCase;
  private final RefundOrderPaymentUseCase refundUseCase;
  private final ConfirmOrderPaymentUseCase confirmUseCase;

  /**
   * @param chargeUseCase payment's real charging logic
   * @param refundUseCase payment's real refunding logic
   * @param confirmUseCase payment's real confirmation logic
   */
  public OrderPaymentAdapter(
      ChargeOrderPaymentUseCase chargeUseCase,
      RefundOrderPaymentUseCase refundUseCase,
      ConfirmOrderPaymentUseCase confirmUseCase) {
    this.chargeUseCase = chargeUseCase;
    this.refundUseCase = refundUseCase;
    this.confirmUseCase = confirmUseCase;
  }

  /**
   * @param orderId the order being charged
   * @param orderNumber the business identifier
   * @param amount the amount to charge
   * @param method how the customer pays
   * @param paymentMethodToken the gateway token for a new card, required for {@code CARD_ONLINE}
   * @param idempotencyKey the checkout's {@code Idempotency-Key}
   * @return a redirect to complete payment, or an already-settled charge
   */
  @Override
  public ChargeResult charge(
      OrderId orderId,
      OrderNumber orderNumber,
      BigDecimal amount,
      PaymentMethod method,
      String paymentMethodToken,
      String idempotencyKey) {
    return chargeUseCase.execute(orderId, orderNumber, amount, method, paymentMethodToken, idempotencyKey);
  }

  /**
   * @param orderId the order whose payment to refund
   * @param orderNumber the original charge's business identifier
   * @return the refunded payment
   */
  @Override
  public Payment refund(OrderId orderId, OrderNumber orderNumber) {
    return refundUseCase.execute(orderId, orderNumber);
  }

  /**
   * @param orderId the order being confirmed
   * @param orderNumber the business identifier
   * @param method how the customer paid
   * @param amount the confirmed amount
   * @param providerPaymentIntentId Redsys's authorization reference from the notification
   * @return the saved, captured payment
   */
  @Override
  public Payment confirmCharge(
      OrderId orderId,
      OrderNumber orderNumber,
      PaymentMethod method,
      BigDecimal amount,
      String providerPaymentIntentId) {
    return confirmUseCase.execute(orderId, orderNumber, method, amount, providerPaymentIntentId);
  }
}
