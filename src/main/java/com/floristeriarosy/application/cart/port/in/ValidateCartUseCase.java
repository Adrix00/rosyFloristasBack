package com.floristeriarosy.application.cart.port.in;

import com.floristeriarosy.application.cart.command.ValidateCartCommand;
import com.floristeriarosy.application.cart.dto.CartValidationDto;

/**
 * Revalidates the cart before payment (cart.md §7, rule 3.4): removes lines whose product is no
 * longer {@code ACTIVE} and reports, without adjusting, lines whose quantity exceeds real stock.
 * Reused by {@code order.md} at checkout, and reachable directly via {@code GET /cart/validation}.
 */
public interface ValidateCartUseCase {

  /**
   * @param command the caller's identity
   * @return whether the cart was already valid, plus what was removed or is blocking checkout
   */
  CartValidationDto execute(ValidateCartCommand command);
}
