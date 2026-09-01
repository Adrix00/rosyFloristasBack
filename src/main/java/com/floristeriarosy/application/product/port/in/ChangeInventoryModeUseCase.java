package com.floristeriarosy.application.product.port.in;

import com.floristeriarosy.application.product.command.ChangeInventoryModeCommand;
import com.floristeriarosy.application.product.dto.ProductDto;

/** Switches a product's inventory mode, managed or unmanaged (product.md, section 7, section 3.7). */
public interface ChangeInventoryModeUseCase {

  /**
   * @param command id of the product to change, plus the new mode and, if managed, its stock
   * @return the updated product
   */
  ProductDto execute(ChangeInventoryModeCommand command);
}
