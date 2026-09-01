package com.floristeriarosy.application.product.port.in;

import com.floristeriarosy.application.product.command.CreateProductCommand;
import com.floristeriarosy.application.product.dto.ProductDto;

/**
 * Creates a product, its categories, its gallery and, if it starts managed, its initial stock —
 * all in one transaction (product.md, section 7).
 */
public interface CreateProductUseCase {

  /**
   * @param command the product's fields, categories, gallery and optional initial stock
   * @return the created product
   */
  ProductDto execute(CreateProductCommand command);
}
