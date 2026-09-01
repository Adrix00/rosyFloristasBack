package com.floristeriarosy.application.product.port.out;

import com.floristeriarosy.application.product.dto.PageResult;
import com.floristeriarosy.application.product.dto.ProductSearchCriteria;
import com.floristeriarosy.application.product.dto.ProductSuggestionDto;
import com.floristeriarosy.application.product.dto.ProductSummaryDto;
import java.util.List;

/**
 * Hides the two ADR-006 search mechanisms behind one capability (ADR-003; product.md, section 8):
 * full-text ({@code search_vector}) for {@link #search}, trigram ({@code search_text}, {@code
 * pg_trgm}) for {@link #autocomplete}. Application never knows which is which.
 */
public interface ProductSearchPort {

  /**
   * Full-text search over visible products only, {@code plainto_tsquery('spanish', ...)} — no
   * prefix matching.
   *
   * @param criteria the combinable filters and the requested page
   * @return the matching visible products, paginated
   */
  PageResult<ProductSummaryDto> search(ProductSearchCriteria criteria);

  /**
   * Trigram autocomplete over visible products only: prefixes and typos.
   *
   * @param q the raw text typed so far, not yet normalized
   * @param limit the maximum number of suggestions to return
   * @return the matching names and slugs, most similar first
   */
  List<ProductSuggestionDto> autocomplete(String q, int limit);
}
