package com.floristeriarosy.infrastructure.persistence.adapter.product;

import com.floristeriarosy.application.product.dto.ProductSummaryDto;
import com.floristeriarosy.application.product.port.out.ProductSuggestionPort;
import com.floristeriarosy.domain.exception.product.ProductSuggestsItselfException;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import com.floristeriarosy.infrastructure.persistence.jdbc.product.repository.ProductSuggestionJdbcRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

/**
 * Implements {@link ProductSuggestionPort} (ADR-003) over JDBC (ADR-002): a pure join-table
 * association, no aggregate to load.
 */
@Repository
public class ProductSuggestionPersistenceAdapter implements ProductSuggestionPort {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProductSuggestionPersistenceAdapter.class);

  private final ProductSuggestionJdbcRepository jdbcRepository;

  /**
   * @param jdbcRepository reads and writes {@code product_suggestions}
   */
  public ProductSuggestionPersistenceAdapter(ProductSuggestionJdbcRepository jdbcRepository) {
    this.jdbcRepository = jdbcRepository;
  }

  /**
   * @param id the product the suggestions are attached to
   * @param extraProductIds every product to suggest, in display order
   * @throws ProductSuggestsItselfException {@code chk_product_suggestions_not_self} was violated —
   *     the service already checks this; this is defense in depth
   */
  @Override
  public void replaceSuggestions(ProductId id, List<ProductId> extraProductIds) {
    LOGGER.debug("replaceSuggestions id={} count={}", id, extraProductIds.size());
    try {
      jdbcRepository.replaceSuggestions(id.value(), extraProductIds.stream().map(ProductId::value).toList());
    } catch (DataIntegrityViolationException violation) {
      String message = String.valueOf(violation.getMostSpecificCause().getMessage());
      if (message.contains("chk_product_suggestions_not_self")) {
        throw new ProductSuggestsItselfException("Product " + id + " cannot suggest itself");
      }
      throw violation;
    }
    LOGGER.debug("replaceSuggestions id={} -> replaced", id);
  }

  /**
   * @param id the product whose suggestions to list
   * @return the suggested extras, already filtered by visibility
   */
  @Override
  public List<ProductSummaryDto> findVisibleSuggestions(ProductId id) {
    LOGGER.debug("findVisibleSuggestions id={}", id);
    List<ProductSummaryDto> result = jdbcRepository.findVisibleSuggestions(id.value());
    LOGGER.debug("findVisibleSuggestions id={} -> count={}", id, result.size());
    return result;
  }
}
