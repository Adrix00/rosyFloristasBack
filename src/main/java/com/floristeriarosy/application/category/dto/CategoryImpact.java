package com.floristeriarosy.application.category.dto;

import java.util.List;

/**
 * @param totalProducts products associated with the category, visible or not
 * @param productsLosingVisibility products that would disappear from the storefront if the category
 *     were deactivated
 * @param productsLeftWithoutCategory products that would be left with zero categories if it were
 *     deleted
 */
public record CategoryImpact(
    long totalProducts,
    List<CategoryProductRef> productsLosingVisibility,
    List<CategoryProductRef> productsLeftWithoutCategory) {

  /** Defensively copies both lists (SpotBugs EI_EXPOSE_REP2). */
  public CategoryImpact {
    productsLosingVisibility = List.copyOf(productsLosingVisibility);
    productsLeftWithoutCategory = List.copyOf(productsLeftWithoutCategory);
  }
}
