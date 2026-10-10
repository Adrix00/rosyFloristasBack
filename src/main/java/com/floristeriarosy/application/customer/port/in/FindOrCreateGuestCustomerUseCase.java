package com.floristeriarosy.application.customer.port.in;

import com.floristeriarosy.application.customer.command.FindOrCreateGuestCustomerCommand;
import com.floristeriarosy.domain.model.customer.valueobject.CustomerId;

/**
 * Consumed directly by {@code order.md}'s {@code ConfirmCheckoutService} (customer.md, section 8)
 * — no controller of its own.
 */
public interface FindOrCreateGuestCustomerUseCase {

  /**
   * @param command the checkout's buyer details
   * @return the linked customer's id, or {@code null} if the email belongs to a {@code
   *     REGISTERED} account (customer.md, rule 3.2 — the order stays unlinked in that case)
   */
  CustomerId execute(FindOrCreateGuestCustomerCommand command);
}
