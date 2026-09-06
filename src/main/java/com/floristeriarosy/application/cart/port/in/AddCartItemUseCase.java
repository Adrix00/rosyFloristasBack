package com.floristeriarosy.application.cart.port.in;

import com.floristeriarosy.application.cart.command.AddCartItemCommand;
import com.floristeriarosy.application.cart.dto.CartDto;
import com.floristeriarosy.domain.exception.cart.CartInsufficientStockException;
import com.floristeriarosy.domain.exception.cart.CartItemLimitExceededException;
import com.floristeriarosy.domain.exception.cart.CartLineLimitExceededException;
import com.floristeriarosy.domain.exception.cart.CartProductNotFoundException;

/** Adds a product to the cart, creating it if it does not exist yet (cart.md §7, rule 3.1). */
public interface AddCartItemUseCase {

  /**
   * @param command the product and quantity to add, plus the caller's identity
   * @return the resulting, fully priced cart
   * @throws CartProductNotFoundException the product does not exist or is not visible (rule 3.6)
   * @throws CartInsufficientStockException the resulting quantity exceeds real stock (rule 3.3)
   * @throws CartItemLimitExceededException the resulting quantity exceeds the per-line maximum
   * @throws CartLineLimitExceededException adding this product would exceed the line-count maximum
   */
  CartDto execute(AddCartItemCommand command);
}
