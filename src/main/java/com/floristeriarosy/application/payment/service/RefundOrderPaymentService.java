package com.floristeriarosy.application.payment.service;

import com.floristeriarosy.application.payment.command.RefundOrderPaymentCommand;
import com.floristeriarosy.application.payment.dto.GatewayRefundParameters;
import com.floristeriarosy.application.payment.dto.GatewayRefundResult;
import com.floristeriarosy.application.payment.port.in.RefundOrderPaymentUseCase;
import com.floristeriarosy.application.payment.port.out.PaymentGatewayPort;
import com.floristeriarosy.application.payment.port.out.PaymentReadPort;
import com.floristeriarosy.application.payment.port.out.PaymentWritePort;
import com.floristeriarosy.domain.exception.payment.PaymentNotFoundException;
import com.floristeriarosy.domain.model.order.valueobject.OrderNumber;
import com.floristeriarosy.domain.model.payment.Payment;
import com.floristeriarosy.domain.model.payment.PaymentMethod;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements {@link RefundOrderPaymentUseCase} (payment.md, rule 3.3): always a full, automatic
 * refund — no decision to make, no partial amount. {@code CARD_ONLINE} calls Redsys synchronously
 * (a refund has no redirect step, unlike a charge, ADR-017); every other method settles directly.
 *
 * <p>The {@link RefundOrderPaymentCommand} carries the order's {@code OrderNumber} because Redsys
 * needs the original {@code Ds_Merchant_Order} to diversify the refund signature (ADR-017). The
 * payment aggregate only stores {@code OrderId}, so the number travels with the command instead of
 * being looked up from the order module or denormalized onto {@code payments}.
 */
@Service
@Transactional
public class RefundOrderPaymentService implements RefundOrderPaymentUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(RefundOrderPaymentService.class);

  private final PaymentReadPort readPort;
  private final PaymentWritePort writePort;
  private final PaymentGatewayPort gatewayPort;

  /**
   * @param readPort locates the order's captured payment
   * @param writePort persists the refund
   * @param gatewayPort the Redsys frontier, used only for {@code CARD_ONLINE}
   */
  public RefundOrderPaymentService(
      PaymentReadPort readPort, PaymentWritePort writePort, PaymentGatewayPort gatewayPort) {
    this.readPort = readPort;
    this.writePort = writePort;
    this.gatewayPort = gatewayPort;
  }

  /**
   * @param command which order's payment to refund
   * @return the refunded, saved payment
   * @throws PaymentNotFoundException the order has no payment associated
   */
  @Override
  public Payment execute(RefundOrderPaymentCommand command) {
    LOGGER.debug("refundOrderPayment orderId={}", command.orderId());
    Payment payment =
        readPort
            .findByOrderId(command.orderId())
            .orElseThrow(() -> new PaymentNotFoundException("Order " + command.orderId() + " has no payment"));
    String providerReference =
        payment.method() == PaymentMethod.CARD_ONLINE ? refundViaRedsys(payment, command.orderNumber()) : null;
    payment.refundInFull(providerReference, Instant.now());
    Payment result = writePort.save(payment);
    LOGGER.debug("refundOrderPayment orderId={} -> refunded", command.orderId());
    return result;
  }

  /**
   * @param payment the captured {@code CARD_ONLINE} payment to refund
   * @param orderNumber the original charge's business identifier, needed by Redsys for signature
   *     diversification
   * @return the gateway's refund reference
   */
  private String refundViaRedsys(Payment payment, OrderNumber orderNumber) {
    GatewayRefundResult result =
        gatewayPort.refund(
            new GatewayRefundParameters(
                orderNumber.value(), payment.providerPaymentIntentId(), payment.amount()));
    if (!result.success()) {
      throw new IllegalStateException("Redsys refused the refund: " + result.failureReason());
    }
    return result.providerReference();
  }
}
