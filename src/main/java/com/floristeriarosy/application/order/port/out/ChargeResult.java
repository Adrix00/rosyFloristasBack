package com.floristeriarosy.application.order.port.out;

import com.floristeriarosy.domain.model.payment.Payment;
import java.util.Map;

/**
 * The outcome of {@link OrderPaymentPort#charge} — either the customer must be redirected to
 * finish paying ({@code CARD_ONLINE}, ADR-017), or the charge is already settled ({@code MANUAL},
 * payment.md section 3.1).
 */
public sealed interface ChargeResult {

  /**
   * @param redirectUrl the Redsys form-post target
   * @param formFields the signed {@code Ds_*} fields the frontend must post as a form
   */
  record RedirectRequired(String redirectUrl, Map<String, String> formFields) implements ChargeResult {}

  /**
   * @param payment the already-captured payment, not yet persisted — the caller decides when to
   *     save it, inside its own transaction (payment.md, section 3.6)
   */
  record Captured(Payment payment) implements ChargeResult {}
}
