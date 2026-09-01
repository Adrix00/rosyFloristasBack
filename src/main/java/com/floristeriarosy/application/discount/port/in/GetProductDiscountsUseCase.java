package com.floristeriarosy.application.discount.port.in;

import com.floristeriarosy.application.discount.dto.DiscountDto;
import com.floristeriarosy.application.discount.query.GetProductDiscountsQuery;
import java.util.List;

/** Lists a product's complete discount history (product-discounts.md, section 4 and 7). */
public interface GetProductDiscountsUseCase {

  /**
   * @param query the product whose discount history to list
   * @return every discount ever created for the product, most recent first
   */
  List<DiscountDto> execute(GetProductDiscountsQuery query);
}
