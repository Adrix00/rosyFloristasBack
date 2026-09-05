package com.floristeriarosy.application.cart.port.in;

import com.floristeriarosy.application.cart.command.MergeCartCommand;

/**
 * Merges a guest cart into a customer's own cart at login time (cart.md §7, rule 3.2). A capability
 * exposed for {@code auth.md} to invoke — no caller exists yet in this branch, since customer login
 * (feature/customer) is not implemented (same status as inventory.md's {@code
 * RegisterStockMovementUseCase} before product.md consumed it).
 */
public interface MergeCartUseCase {

  /**
   * @param command the customer who just logged in, and the guest session token present at login
   */
  void execute(MergeCartCommand command);
}
