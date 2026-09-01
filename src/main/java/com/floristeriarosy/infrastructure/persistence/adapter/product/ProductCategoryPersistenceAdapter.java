package com.floristeriarosy.infrastructure.persistence.adapter.product;

import com.floristeriarosy.application.product.dto.ProductCategoryRef;
import com.floristeriarosy.application.product.port.out.ProductCategoryPort;
import com.floristeriarosy.domain.model.category.valueobject.CategoryId;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import com.floristeriarosy.infrastructure.persistence.jdbc.product.repository.ProductCategoryJdbcRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

/**
 * Implements {@link ProductCategoryPort} (ADR-003) over JDBC (ADR-002): a pure join-table
 * association, no aggregate to load.
 */
@Repository
public class ProductCategoryPersistenceAdapter implements ProductCategoryPort {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProductCategoryPersistenceAdapter.class);

  private final ProductCategoryJdbcRepository jdbcRepository;

  /**
   * @param jdbcRepository reads and writes {@code product_categories}
   */
  public ProductCategoryPersistenceAdapter(ProductCategoryJdbcRepository jdbcRepository) {
    this.jdbcRepository = jdbcRepository;
  }

  /**
   * @param id the product whose categories are being set
   * @param categoryIds every category id the product should belong to
   */
  @Override
  public void replaceCategories(ProductId id, List<CategoryId> categoryIds) {
    LOGGER.debug("replaceCategories id={} count={}", id, categoryIds.size());
    jdbcRepository.replaceCategories(id.value(), categoryIds.stream().map(CategoryId::value).toList());
    LOGGER.debug("replaceCategories id={} -> replaced", id);
  }

  /**
   * @param id the product to look up
   * @return the categories it belongs to
   */
  @Override
  public List<ProductCategoryRef> findCategories(ProductId id) {
    LOGGER.debug("findCategories id={}", id);
    List<ProductCategoryRef> result = jdbcRepository.findCategories(id.value());
    LOGGER.debug("findCategories id={} -> count={}", id, result.size());
    return result;
  }
}
