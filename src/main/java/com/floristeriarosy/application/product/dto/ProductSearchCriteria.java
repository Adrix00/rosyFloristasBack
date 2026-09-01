package com.floristeriarosy.application.product.dto;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Filter criteria for {@code ProductSearchPort#search} (product.md, section 4: {@code GET
 * /products}). Built by {@code SearchProductsService} from a {@code SearchProductsQuery}, with
 * {@code attributeFilters} already validated and type-coerced against the declared attribute
 * definitions (product.md, section 3.5) — the persistence adapter never talks to {@code
 * AttributeDefinitionPort}.
 *
 * @param q free text to match against {@code search_vector}, or {@code null}
 * @param categoryIdOrSlug a category's id or slug the product must belong to, or {@code null}
 * @param minPrice minimum effective price (with an active discount applied, if any), or {@code
 *     null}
 * @param maxPrice maximum effective price, or {@code null}
 * @param onSale whether to only return products with a currently active discount
 * @param attributeFilters declared, filterable attribute keys mapped to their already-typed
 *     value ({@link String}, {@link BigDecimal} or {@link Boolean})
 * @param page requested page, zero-based
 * @param size requested page size
 */
public record ProductSearchCriteria(
    String q,
    String categoryIdOrSlug,
    BigDecimal minPrice,
    BigDecimal maxPrice,
    boolean onSale,
    Map<String, Object> attributeFilters,
    int page,
    int size) {

  /** Defensively copies {@code attributeFilters} (SpotBugs EI_EXPOSE_REP2). */
  public ProductSearchCriteria {
    attributeFilters = Map.copyOf(attributeFilters);
  }
}
