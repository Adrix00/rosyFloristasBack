package com.floristeriarosy.application.product.port.in;

import com.floristeriarosy.application.product.command.DeleteProductCommand;

/** Permanently deletes a product with no commercial history (product.md, section 7, section 3.10). */
public interface DeleteProductUseCase {

  /**
   * @param command id of the product to delete
   */
  void execute(DeleteProductCommand command);
}
