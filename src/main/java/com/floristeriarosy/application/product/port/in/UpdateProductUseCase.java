package com.floristeriarosy.application.product.port.in;

import com.floristeriarosy.application.product.command.UpdateProductCommand;
import com.floristeriarosy.application.product.dto.ProductDto;

/** Full replace ({@code PUT}) of a product's own fields (product.md, section 7). */
public interface UpdateProductUseCase {

  /**
   * @param command id of the product to update, plus its new field values
   * @return the updated product
   */
  ProductDto execute(UpdateProductCommand command);
}
