package com.floristeriarosy.application.category.dto;

import java.util.List;

public record CategoryImpact(
    long totalProducts,
    List<CategoryProductRef> productsLosingVisibility,
    List<CategoryProductRef> productsLeftWithoutCategory) {

  public CategoryImpact {
    productsLosingVisibility = List.copyOf(productsLosingVisibility);
    productsLeftWithoutCategory = List.copyOf(productsLeftWithoutCategory);
  }
}
