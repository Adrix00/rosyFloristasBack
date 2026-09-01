package com.floristeriarosy.application.product.port.in;

import com.floristeriarosy.application.product.dto.PageResult;
import com.floristeriarosy.application.product.dto.ProductSummaryDto;
import com.floristeriarosy.application.product.query.SearchProductsQuery;

/**
 * Public, paginated product search: {@code GET /products} (product.md, section 4 and 7). Only
 * visible products (status {@code ACTIVE} with at least one {@code ACTIVE} category).
 */
public interface SearchProductsUseCase {

  /**
   * @param query the combinable filters and the requested page
   * @return the matching visible products, paginated
   */
  PageResult<ProductSummaryDto> execute(SearchProductsQuery query);
}
