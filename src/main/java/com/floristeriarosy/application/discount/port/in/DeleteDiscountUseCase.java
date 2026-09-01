package com.floristeriarosy.application.discount.port.in;

import com.floristeriarosy.application.discount.command.DeleteDiscountCommand;

/**
 * Permanently deletes a discount that has not started yet (product-discounts.md, section 3.4).
 */
public interface DeleteDiscountUseCase {

  /**
   * @param command id of the discount to delete
   */
  void execute(DeleteDiscountCommand command);
}
