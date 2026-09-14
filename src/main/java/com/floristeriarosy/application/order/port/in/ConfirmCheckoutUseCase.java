package com.floristeriarosy.application.order.port.in;

import com.floristeriarosy.application.order.command.ConfirmCheckoutCommand;

/** Resolves a Redsys notification, approved or declined (order.md §3.2, ADR-017). */
public interface ConfirmCheckoutUseCase {

  /**
   * @param command the decoded, signature-verified notification
   */
  void execute(ConfirmCheckoutCommand command);
}
