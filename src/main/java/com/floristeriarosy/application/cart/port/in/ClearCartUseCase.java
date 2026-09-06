package com.floristeriarosy.application.cart.port.in;

import com.floristeriarosy.application.cart.command.ClearCartCommand;
import com.floristeriarosy.application.cart.dto.CartDto;

/** Empties the cart without deleting it (cart.md §4, §7). */
public interface ClearCartUseCase {

  /**
   * @param command the caller's identity
   * @return the resulting, empty cart; a no-op on an already-empty or nonexistent cart, not an
   *     error (cart.md §10)
   */
  CartDto execute(ClearCartCommand command);
}
