package com.floristeriarosy.application.product.port.in;

import com.floristeriarosy.application.product.dto.ProductSuggestionDto;
import com.floristeriarosy.application.product.query.AutocompleteProductsQuery;
import java.util.List;

/**
 * Trigram autocomplete for the search bar's dropdown: {@code GET /products/suggestions}
 * (product.md, section 4 and 7; ADR-006). Distinct from {@link SearchProductsUseCase}: this
 * matches prefixes and tolerates typos, {@link SearchProductsUseCase} does not.
 */
public interface AutocompleteProductsUseCase {

  /**
   * @param query the text typed so far
   * @return the matching visible product names and slugs, most similar first
   */
  List<ProductSuggestionDto> execute(AutocompleteProductsQuery query);
}
