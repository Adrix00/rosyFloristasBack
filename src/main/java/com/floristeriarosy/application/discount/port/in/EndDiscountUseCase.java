package com.floristeriarosy.application.discount.port.in;

import com.floristeriarosy.application.discount.command.EndDiscountCommand;
import com.floristeriarosy.application.discount.dto.DiscountDto;

/** Closes a discount now, setting {@code endsAt = now()} (product-discounts.md, section 3.4). */
public interface EndDiscountUseCase {

  /**
   * @param command id of the discount to close
   * @return the closed discount
   */
  DiscountDto execute(EndDiscountCommand command);
}
