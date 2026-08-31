package com.floristeriarosy.infrastructure.web.response.category;

import java.util.List;

/**
 * @param totalProducts products associated with the category, visible or not
 * @param productsLosingVisibility products that would disappear from the storefront if the category
 *     were deactivated
 * @param productsLeftWithoutCategory products that would be left with zero categories if it were
 *     deleted
 */
public record CategoryImpactResponse(
    long totalProducts,
    List<CategoryProductRefResponse> productsLosingVisibility,
    List<CategoryProductRefResponse> productsLeftWithoutCategory) {

  /** Defensively copies both lists (SpotBugs EI_EXPOSE_REP2). */
  public CategoryImpactResponse {
    productsLosingVisibility = List.copyOf(productsLosingVisibility);
    productsLeftWithoutCategory = List.copyOf(productsLeftWithoutCategory);
  }
}
