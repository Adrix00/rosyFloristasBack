package com.floristeriarosy.infrastructure.persistence.jdbc.product.repository;

import com.floristeriarosy.application.product.dto.ProductSummaryDto;
import com.floristeriarosy.infrastructure.persistence.jdbc.product.rowmapper.ProductSummaryRowMapper;
import com.floristeriarosy.infrastructure.persistence.support.product.ProductActiveSalePriceSql;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** JDBC reads and writes for a product's suggested extras (ADR-002, product.md section 3.6). */
@Repository
public class ProductSuggestionJdbcRepository {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProductSuggestionJdbcRepository.class);

  private static final String FIND_VISIBLE_SQL =
      "SELECT p.id, p.name, p.slug, p.price, ("
          + ProductActiveSalePriceSql.CORRELATED_SUBQUERY
          + ") AS active_sale_price "
          + """
          FROM product_suggestions ps
          JOIN products p ON p.id = ps.suggested_product_id
          WHERE ps.product_id = ?
            AND p.status = 'ACTIVE'
            AND EXISTS (
              SELECT 1 FROM product_categories pc
              JOIN categories c ON c.id = pc.category_id AND c.status = 'ACTIVE'
              WHERE pc.product_id = p.id
            )
          ORDER BY ps.position
          """;

  private final JdbcTemplate jdbcTemplate;
  private final ProductSummaryRowMapper rowMapper = new ProductSummaryRowMapper();

  /**
   * @param jdbcTemplate runs the SQL against the configured datasource
   */
  public ProductSuggestionJdbcRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * Deletes every existing suggestion for {@code productId} and inserts {@code
   * extraProductIds}; the list index becomes {@code position}.
   *
   * @param productId the product the suggestions are attached to
   * @param extraProductIds every product to suggest, in display order
   */
  public void replaceSuggestions(UUID productId, List<UUID> extraProductIds) {
    LOGGER.debug("replaceSuggestions productId={} count={}", productId, extraProductIds.size());
    jdbcTemplate.update("DELETE FROM product_suggestions WHERE product_id = ?", productId);
    Instant now = Instant.now();
    List<Object[]> params =
        java.util.stream.IntStream.range(0, extraProductIds.size())
            .mapToObj(index -> new Object[] {productId, extraProductIds.get(index), index, now})
            .toList();
    jdbcTemplate.batchUpdate(
        "INSERT INTO product_suggestions (product_id, suggested_product_id, position, created_at) VALUES (?, ?, ?, ?)",
        params);
    LOGGER.debug("replaceSuggestions productId={} -> {} rows inserted", productId, extraProductIds.size());
  }

  /**
   * @param productId the product whose suggestions to list
   * @return the suggested extras, already filtered by visibility (product.md, section 3.6)
   */
  public List<ProductSummaryDto> findVisibleSuggestions(UUID productId) {
    LOGGER.debug("findVisibleSuggestions productId={}", productId);
    List<ProductSummaryDto> result = jdbcTemplate.query(FIND_VISIBLE_SQL, rowMapper, productId);
    LOGGER.debug("findVisibleSuggestions productId={} -> count={}", productId, result.size());
    return result;
  }
}
