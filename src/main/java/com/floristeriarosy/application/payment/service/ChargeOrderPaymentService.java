package com.floristeriarosy.application.payment.service;

import com.floristeriarosy.application.order.port.out.ChargeResult;
import com.floristeriarosy.application.payment.command.ChargeOrderPaymentCommand;
import com.floristeriarosy.application.payment.port.in.ChargeOrderPaymentUseCase;
import com.floristeriarosy.application.payment.port.out.GatewayChargeRequest;
import com.floristeriarosy.application.payment.port.out.PaymentGatewayPort;
import com.floristeriarosy.application.payment.port.out.RedsysRedirect;
import com.floristeriarosy.domain.model.payment.Payment;
import com.floristeriarosy.domain.model.payment.PaymentProvider;
import com.floristeriarosy.domain.model.payment.valueobject.PaymentId;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Implements {@link ChargeOrderPaymentUseCase} (payment.md, rule 3.2): a single method that
 * branches by {@code method} — {@code CARD_ONLINE} defers to Redsys via a redirect, everything else
 * captures directly, no external call. Writes nothing to {@code payments} itself; the returned
 * {@link ChargeResult.Captured} payment is unsaved — the caller persists it inside its own
 * transaction (Fase 1+3a fused for counter sales, payment.md rule 3.6).
 */
@Service
public class ChargeOrderPaymentService implements ChargeOrderPaymentUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(ChargeOrderPaymentService.class);

  private final PaymentGatewayPort gatewayPort;

  /**
   * @param gatewayPort the Redsys frontier, used only for {@code CARD_ONLINE}
   */
  public ChargeOrderPaymentService(PaymentGatewayPort gatewayPort) {
    this.gatewayPort = gatewayPort;
  }

  /**
   * @param command what to charge and how
   * @return a redirect to complete payment ({@code CARD_ONLINE}), or an already-captured, unsaved
   *     payment (every other method)
   */
  @Override
  public ChargeResult execute(ChargeOrderPaymentCommand command) {
    LOGGER.debug(
        "chargeOrderPayment orderId={} method={} amount={}", command.orderId(), command.method(),
        command.amount());
    ChargeResult result =
        switch (command.method()) {
          case CARD_ONLINE -> chargeViaRedsys(command);
          case CASH, DATAPHONE, INTERFLORA -> captureDirectly(command);
        };
    LOGGER.debug("chargeOrderPayment orderId={} -> {}", command.orderId(), result.getClass().getSimpleName());
    return result;
  }

  /**
   * @param command the charge to build a redirect for
   * @return the redirect the customer must be sent to
   */
  private ChargeResult chargeViaRedsys(ChargeOrderPaymentCommand command) {
    RedsysRedirect redirect =
        gatewayPort.charge(
            new GatewayChargeRequest(
                command.orderNumber(), command.amount(), command.paymentMethodToken(), command.idempotencyKey()));
    return new ChargeResult.RedirectRequired(redirect.redirectUrl(), redirect.formFields());
  }

  /**
   * @param command the charge to capture directly, no gateway involved
   * @return the already-captured, unsaved payment
   */
  private ChargeResult captureDirectly(ChargeOrderPaymentCommand command) {
    Payment payment =
        Payment.initiate(
            PaymentId.newId(), command.orderId(), command.method(), PaymentProvider.MANUAL, command.amount());
    payment.capture(null, Instant.now());
    return new ChargeResult.Captured(payment);
  }
}
