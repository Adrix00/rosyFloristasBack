package com.floristeriarosy.application.payment.port.out;

/**
 * The frontier with the real payment gateway (payment.md, section 8): isolates Redsys from the
 * rest of this module. Only ever called for {@code CARD_ONLINE} — {@code MANUAL} methods never
 * reach this port (payment.md, section 3.1).
 */
public interface PaymentGatewayPort {

  /**
   * @param request what to charge
   * @return the signed redirect the customer must be sent to
   */
  RedsysRedirect charge(GatewayChargeRequest request);

  /**
   * @param request what to refund
   * @return whether the gateway confirmed the refund
   */
  GatewayRefundResult refund(GatewayRefundRequest request);
}
