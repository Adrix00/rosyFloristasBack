package com.floristeriarosy.application.product.port.in;

import com.floristeriarosy.application.product.dto.PageResult;
import com.floristeriarosy.application.product.dto.ProductSummaryDto;
import com.floristeriarosy.application.product.query.GetProductsQuery;

/**
 * Admin product listing: {@code GET /products/all} (ADMIN; product.md, section 4 and 7). Unlike
 * {@link SearchProductsUseCase}, ignores visibility — every status is returned.
 */
public interface GetProductsUseCase {

  /**
   * @param query the combinable filters and the requested page
   * @return the matching products, paginated, regardless of visibility
   */
  PageResult<ProductSummaryDto> execute(GetProductsQuery query);
}
