package com.floristeriarosy.application.product.port.in;

import com.floristeriarosy.application.product.command.UpdateProductCategoriesCommand;
import com.floristeriarosy.application.product.dto.ProductDto;

/** Replaces a product's full set of categories (product.md, section 7). */
public interface UpdateProductCategoriesUseCase {

  /**
   * @param command id of the product to update, plus its complete new category set
   * @return the updated product
   */
  ProductDto execute(UpdateProductCategoriesCommand command);
}
