package com.floristeriarosy.application.product.port.in;

import com.floristeriarosy.application.product.dto.ProductDeletionImpact;
import com.floristeriarosy.application.product.query.GetProductDeletionImpactQuery;

/** Previews whether a product can be physically deleted, and why not (product.md, section 7, section 3.10). */
public interface GetProductDeletionImpactUseCase {

  /**
   * @param query the product being previewed for deletion
   * @return the impact preview
   */
  ProductDeletionImpact execute(GetProductDeletionImpactQuery query);
}
