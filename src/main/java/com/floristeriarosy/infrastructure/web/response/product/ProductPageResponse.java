package com.floristeriarosy.infrastructure.web.response.product;

import java.util.List;

/**
 * A paginated product listing, shared by {@code GET /products} and {@code GET /products/all}
 * (product.md, section 4).
 *
 * @param items the products on this page
 * @param totalElements the total number of matching products across every page
 * @param page the requested page, zero-based
 * @param size the requested page size
 */
public record ProductPageResponse(
    List<ProductSummaryResponse> items, long totalElements, int page, int size) {

  /** Defensively copies {@code items} (SpotBugs EI_EXPOSE_REP2). */
  public ProductPageResponse {
    items = List.copyOf(items);
  }
}
