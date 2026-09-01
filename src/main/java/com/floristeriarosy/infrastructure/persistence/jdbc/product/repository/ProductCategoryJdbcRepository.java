package com.floristeriarosy.infrastructure.persistence.jdbc.product.repository;

import com.floristeriarosy.application.product.dto.ProductCategoryRef;
import com.floristeriarosy.infrastructure.persistence.jdbc.product.rowmapper.ProductCategoryRefRowMapper;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** JDBC reads and writes for a product's category associations (ADR-002). */
@Repository
public class ProductCategoryJdbcRepository {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProductCategoryJdbcRepository.class);

  private static final String FIND_SQL =
      """
      SELECT c.id, c.name, c.slug FROM product_categories pc
      JOIN categories c ON c.id = pc.category_id
      WHERE pc.product_id = ?
      ORDER BY c.position, c.name
      """;

  private final JdbcTemplate jdbcTemplate;
  private final ProductCategoryRefRowMapper rowMapper = new ProductCategoryRefRowMapper();

  /**
   * @param jdbcTemplate runs the SQL against the configured datasource
   */
  public ProductCategoryJdbcRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * Deletes every existing association for {@code productId} and inserts {@code categoryIds}.
   *
   * @param productId the product whose categories are being set
   * @param categoryIds every category id the product should belong to
   */
  public void replaceCategories(UUID productId, List<UUID> categoryIds) {
    LOGGER.debug("replaceCategories productId={} count={}", productId, categoryIds.size());
    jdbcTemplate.update("DELETE FROM product_categories WHERE product_id = ?", productId);
    List<Object[]> params = categoryIds.stream().map(categoryId -> new Object[] {productId, categoryId}).toList();
    jdbcTemplate.batchUpdate("INSERT INTO product_categories (product_id, category_id) VALUES (?, ?)", params);
    LOGGER.debug("replaceCategories productId={} -> {} rows inserted", productId, categoryIds.size());
  }

  /**
   * @param productId the product to look up
   * @return the categories it belongs to
   */
  public List<ProductCategoryRef> findCategories(UUID productId) {
    LOGGER.debug("findCategories productId={}", productId);
    List<ProductCategoryRef> result = jdbcTemplate.query(FIND_SQL, rowMapper, productId);
    LOGGER.debug("findCategories productId={} -> count={}", productId, result.size());
    return result;
  }
}
