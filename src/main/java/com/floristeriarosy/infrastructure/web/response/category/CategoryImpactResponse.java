package com.floristeriarosy.infrastructure.web.response.category;

import java.util.List;

public record CategoryImpactResponse(
    long totalProducts,
    List<CategoryProductRefResponse> productsLosingVisibility,
    List<CategoryProductRefResponse> productsLeftWithoutCategory) {

  public CategoryImpactResponse {
    productsLosingVisibility = List.copyOf(productsLosingVisibility);
    productsLeftWithoutCategory = List.copyOf(productsLeftWithoutCategory);
  }
}
