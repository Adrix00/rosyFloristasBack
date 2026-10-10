package com.floristeriarosy.application.order.port.in;

import com.floristeriarosy.application.order.command.ConfirmCheckoutCommand;
import com.floristeriarosy.application.order.dto.ConfirmCheckoutOutcome;

/** Resolves a Redsys notification, approved or declined (order.md §3.2, ADR-017). */
public interface ConfirmCheckoutUseCase {

  /**
   * @param command the decoded, signature-verified notification
   * @return what was found under the row lock: resolved now, nothing to do, or already expired
   */
  ConfirmCheckoutOutcome execute(ConfirmCheckoutCommand command);
}
