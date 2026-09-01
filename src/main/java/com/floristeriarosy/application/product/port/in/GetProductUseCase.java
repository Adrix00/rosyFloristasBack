package com.floristeriarosy.application.product.port.in;

import com.floristeriarosy.application.product.dto.ProductDto;
import com.floristeriarosy.application.product.query.GetProductQuery;

/** Loads a single product by id or slug (product.md, section 7, section 4). */
public interface GetProductUseCase {

  /**
   * @param query the raw path segment: a UUID or a slug
   * @return the matching product
   */
  ProductDto execute(GetProductQuery query);
}
