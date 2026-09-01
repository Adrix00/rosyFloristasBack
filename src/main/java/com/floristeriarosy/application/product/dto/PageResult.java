package com.floristeriarosy.application.product.dto;

import java.util.List;

/**
 * A page of results, technology-agnostic (ADR-002: Application never knows about {@code
 * org.springframework.data.domain.Page}). Shared by {@code SearchProductsUseCase} and {@code
 * GetProductsUseCase} — both page a {@link ProductSummaryDto} listing.
 *
 * @param <T> the type of one item in the page
 * @param items the items on this page
 * @param totalElements the total number of matching items across every page
 * @param page the requested page, zero-based
 * @param size the requested page size
 */
public record PageResult<T>(List<T> items, long totalElements, int page, int size) {

  /** Defensively copies {@code items} (SpotBugs EI_EXPOSE_REP2). */
  public PageResult {
    items = List.copyOf(items);
  }
}
