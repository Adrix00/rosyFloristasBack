package com.floristeriarosy.application.product.port.in;

import com.floristeriarosy.application.product.command.ChangeProductStatusCommand;
import com.floristeriarosy.application.product.dto.ProductDto;

/** Changes a product's status (product.md, section 7). */
public interface ChangeProductStatusUseCase {

  /**
   * @param command id of the product to change, plus its new status
   * @return the updated product
   */
  ProductDto execute(ChangeProductStatusCommand command);
}
