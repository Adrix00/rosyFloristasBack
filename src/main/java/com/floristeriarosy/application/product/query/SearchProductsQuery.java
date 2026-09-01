package com.floristeriarosy.application.product.query;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Input of {@code SearchProductsUseCase}: {@code GET /products} (product.md, section 4). Every
 * filter is optional and combinable.
 *
 * @param q free text, matched full-text against {@code search_vector} ({@code
 *     plainto_tsquery('spanish', ...)}, no prefix matching — ADR-006), or {@code null}
 * @param category a category's id or slug the product must belong to, or {@code null}
 * @param minPrice minimum effective price, or {@code null}
 * @param maxPrice maximum effective price, or {@code null}
 * @param onSale whether to only return products with a currently active discount
 * @param attributeFilters raw {@code attr.{key}=value} query parameters, key without the {@code
 *     attr.} prefix; validated and type-coerced by the service
 * @param page requested page, zero-based, clamped to {@code >= 0}
 * @param size requested page size, clamped to {@code [1, MAX_PAGE_SIZE]}
 */
public record SearchProductsQuery(
    String q,
    String category,
    BigDecimal minPrice,
    BigDecimal maxPrice,
    boolean onSale,
    Map<String, String> attributeFilters,
    int page,
    int size) {

  /** Defensively copies {@code attributeFilters} (SpotBugs EI_EXPOSE_REP2). */
  public SearchProductsQuery {
    attributeFilters = Map.copyOf(attributeFilters);
  }
}
