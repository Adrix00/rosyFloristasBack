package com.floristeriarosy.application.discount.port.in;

import com.floristeriarosy.application.discount.command.CreateDiscountCommand;
import com.floristeriarosy.application.discount.dto.DiscountDto;

/** Creates a new promotional price for a product (product-discounts.md, section 7). */
public interface CreateDiscountUseCase {

  /**
   * @param command the product to discount, plus the promotion's fields
   * @return the created discount
   */
  DiscountDto execute(CreateDiscountCommand command);
}
