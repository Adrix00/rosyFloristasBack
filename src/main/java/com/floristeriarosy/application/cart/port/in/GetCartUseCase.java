package com.floristeriarosy.application.cart.port.in;

import com.floristeriarosy.application.cart.dto.CartDto;
import com.floristeriarosy.application.cart.query.GetCartQuery;

/** Reads the fully priced cart (cart.md §7). Read-only: never creates a cart (rule 3.1). */
public interface GetCartUseCase {

  /**
   * @param query the caller's identity
   * @return the fully priced cart, or the empty virtual cart if none has ever been created
   */
  CartDto execute(GetCartQuery query);
}
