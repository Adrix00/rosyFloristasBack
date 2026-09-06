package com.floristeriarosy.application.cart.port.in;

import com.floristeriarosy.application.cart.command.UpdateCartItemCommand;
import com.floristeriarosy.application.cart.dto.CartDto;
import com.floristeriarosy.domain.exception.cart.CartInsufficientStockException;
import com.floristeriarosy.domain.exception.cart.CartItemLimitExceededException;
import com.floristeriarosy.domain.exception.cart.CartItemNotFoundException;

/** Fixes a cart line's quantity to an exact value (cart.md §7). */
public interface UpdateCartItemUseCase {

  /**
   * @param command the product and the exact quantity to set, plus the caller's identity
   * @return the resulting, fully priced cart
   * @throws CartItemNotFoundException the product has no line in the cart
   * @throws CartInsufficientStockException the requested quantity exceeds real stock (rule 3.3)
   * @throws CartItemLimitExceededException the requested quantity exceeds the per-line maximum
   */
  CartDto execute(UpdateCartItemCommand command);
}
