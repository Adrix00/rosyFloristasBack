package com.floristeriarosy.application.product.port.in;

import com.floristeriarosy.application.product.command.UpdateProductImagesCommand;
import com.floristeriarosy.application.product.dto.ProductDto;

/** Replaces a product's full image gallery, in order (product.md, section 7). */
public interface UpdateProductImagesUseCase {

  /**
   * @param command id of the product to update, plus its complete new gallery
   * @return the updated product
   */
  ProductDto execute(UpdateProductImagesCommand command);
}
