package com.floristeriarosy.application.product.port.in;

import com.floristeriarosy.application.product.command.UpdateProductExtrasCommand;
import com.floristeriarosy.application.product.dto.ProductDto;

/** Replaces a product's full set of suggested extras (product.md, section 7, section 3.6). */
public interface UpdateProductExtrasUseCase {

  /**
   * @param command id of the product to update, plus its complete new suggestion set
   * @return the updated product
   */
  ProductDto execute(UpdateProductExtrasCommand command);
}
