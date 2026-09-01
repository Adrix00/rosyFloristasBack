package com.floristeriarosy.infrastructure.persistence.adapter.product;

import com.floristeriarosy.application.product.dto.PageResult;
import com.floristeriarosy.application.product.dto.ProductSearchCriteria;
import com.floristeriarosy.application.product.dto.ProductSuggestionDto;
import com.floristeriarosy.application.product.dto.ProductSummaryDto;
import com.floristeriarosy.application.product.port.out.ProductSearchPort;
import com.floristeriarosy.infrastructure.persistence.jdbc.product.repository.ProductSearchJdbcRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

/** Implements {@link ProductSearchPort} (ADR-003): JDBC only, per ADR-002 and ADR-006. */
@Repository
public class ProductSearchPersistenceAdapter implements ProductSearchPort {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProductSearchPersistenceAdapter.class);

  private final ProductSearchJdbcRepository jdbcRepository;

  /**
   * @param jdbcRepository runs the full-text search and the trigram autocomplete
   */
  public ProductSearchPersistenceAdapter(ProductSearchJdbcRepository jdbcRepository) {
    this.jdbcRepository = jdbcRepository;
  }

  /**
   * @param criteria the combinable filters and the requested page
   * @return the matching visible products, paginated
   */
  @Override
  public PageResult<ProductSummaryDto> search(ProductSearchCriteria criteria) {
    LOGGER.debug("search page={} size={}", criteria.page(), criteria.size());
    PageResult<ProductSummaryDto> result = jdbcRepository.search(criteria);
    LOGGER.debug("search -> totalElements={}", result.totalElements());
    return result;
  }

  /**
   * @param q the raw text typed so far, not yet normalized
   * @param limit the maximum number of suggestions to return
   * @return the matching names and slugs, most similar first
   */
  @Override
  public List<ProductSuggestionDto> autocomplete(String q, int limit) {
    LOGGER.debug("autocomplete limit={}", limit);
    List<ProductSuggestionDto> result = jdbcRepository.autocomplete(q, limit);
    LOGGER.debug("autocomplete -> count={}", result.size());
    return result;
  }
}
