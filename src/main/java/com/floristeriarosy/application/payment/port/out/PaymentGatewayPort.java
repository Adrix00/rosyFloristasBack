package com.floristeriarosy.application.payment.port.out;

import com.floristeriarosy.application.payment.dto.GatewayChargeParameters;
import com.floristeriarosy.application.payment.dto.GatewayRefundParameters;
import com.floristeriarosy.application.payment.dto.GatewayRefundResult;
import com.floristeriarosy.application.payment.dto.RedsysRedirect;
import java.math.BigDecimal;

/**
 * The frontier with the real payment gateway (payment.md, section 8): isolates Redsys from the
 * rest of this module. Only ever called for {@code CARD_ONLINE} — {@code MANUAL} methods never
 * reach this port (payment.md, section 3.1).
 */
public interface PaymentGatewayPort {

  /**
   * @param parameters what to charge
   * @return the signed redirect the customer must be sent to
   */
  RedsysRedirect charge(GatewayChargeParameters parameters);

  /**
   * @param parameters what to refund
   * @return whether the gateway confirmed the refund
   */
  GatewayRefundResult refund(GatewayRefundParameters parameters);

  /**
   * Refunds a charge identified only by the gateway's own order id — the case of a charge approved
   * after its checkout expired, where no order (and so no order number) exists.
   *
   * @param gatewayOrderId the order id the gateway received with the charge
   * @param amount the full amount charged
   * @return whether the gateway confirmed the refund
   */
  GatewayRefundResult refundByGatewayOrderId(String gatewayOrderId, BigDecimal amount);
}
