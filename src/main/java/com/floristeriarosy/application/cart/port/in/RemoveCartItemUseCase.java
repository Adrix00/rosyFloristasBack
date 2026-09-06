package com.floristeriarosy.application.cart.port.in;

import com.floristeriarosy.application.cart.command.RemoveCartItemCommand;
import com.floristeriarosy.application.cart.dto.CartDto;
import com.floristeriarosy.domain.exception.cart.CartItemNotFoundException;

/** Removes a single line from the cart (cart.md §7). */
public interface RemoveCartItemUseCase {

  /**
   * @param command the product to remove, plus the caller's identity
   * @return the resulting, fully priced cart
   * @throws CartItemNotFoundException the product has no line in the cart
   */
  CartDto execute(RemoveCartItemCommand command);
}
