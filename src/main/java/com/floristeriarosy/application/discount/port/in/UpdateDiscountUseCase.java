package com.floristeriarosy.application.discount.port.in;

import com.floristeriarosy.application.discount.command.UpdateDiscountCommand;
import com.floristeriarosy.application.discount.dto.DiscountDto;

/**
 * Applies a partial edit to a discount, per the editability rules of product-discounts.md, section
 * 3.3.
 */
public interface UpdateDiscountUseCase {

  /**
   * @param command id of the discount to update, plus the requested new field values
   * @return the updated discount
   */
  DiscountDto execute(UpdateDiscountCommand command);
}
