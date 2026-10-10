package com.floristeriarosy.application.payment.port.in;

import com.floristeriarosy.application.order.dto.ChargeResult;
import com.floristeriarosy.application.payment.command.ChargeOrderPaymentCommand;
import com.floristeriarosy.domain.model.order.valueobject.OrderId;
import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;
import com.floristeriarosy.domain.model.payment.PaymentMethod;
import java.math.BigDecimal;

/** Implements {@code OrderPaymentPort.charge} (payment.md, section 7). */
public interface ChargeOrderPaymentUseCase {

  /**
   * @param command what to charge and how
   * @return a redirect to complete payment, or an already-settled charge
   */
  ChargeResult execute(ChargeOrderPaymentCommand command);

  /**
   * Convenience overload for callers that must not depend on {@link ChargeOrderPaymentCommand} — e.g.
   * {@code OrderPaymentAdapter}, an {@code infrastructure.persistence} class barred from the {@code
   * application..command} package (InfrastructureArchitectureTest: {@code
   * persistence_should_not_depend_on_commands}). Delegates to {@link #execute(ChargeOrderPaymentCommand)}.
   *
   * @param orderId the order being charged
   * @param orderNumber the business identifier, becomes {@code Ds_Merchant_Order} for {@code
   *     CARD_ONLINE} (ADR-017)
   * @param amount the amount to charge
   * @param method how the customer pays
   * @param paymentMethodToken the gateway token for a new card, required for {@code CARD_ONLINE}
   * @param idempotencyKey becomes {@code Ds_Merchant_MerchantData} (ADR-017)
   * @return a redirect to complete payment, or an already-settled charge
   */
  default ChargeResult execute(
      OrderId orderId,
      OrderNumber orderNumber,
      BigDecimal amount,
      PaymentMethod method,
      String paymentMethodToken,
      String idempotencyKey) {
    return execute(
        new ChargeOrderPaymentCommand(orderId, orderNumber, amount, method, paymentMethodToken, idempotencyKey));
  }
}
