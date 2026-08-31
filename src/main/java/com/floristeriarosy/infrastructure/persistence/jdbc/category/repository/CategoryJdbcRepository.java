package com.floristeriarosy.infrastructure.persistence.jdbc.category.repository;

import com.floristeriarosy.application.category.dto.CategoryProductRef;
import com.floristeriarosy.domain.model.category.Category;
import com.floristeriarosy.infrastructure.persistence.jdbc.category.rowmapper.CategoryProductRefRowMapper;
import com.floristeriarosy.infrastructure.persistence.jdbc.category.rowmapper.CategoryRowMapper;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** JDBC reads for category (ADR-002): ordered listings and the impact-preview joins. */
@Repository
public class CategoryJdbcRepository {

  private static final Logger LOGGER = LoggerFactory.getLogger(CategoryJdbcRepository.class);

  private static final String SELECT_ALL = "SELECT * FROM categories ORDER BY position, name";
  private static final String SELECT_ACTIVE =
      "SELECT * FROM categories WHERE status = 'ACTIVE' ORDER BY position, name";

  private final JdbcTemplate jdbcTemplate;
  private final CategoryRowMapper categoryRowMapper = new CategoryRowMapper();
  private final CategoryProductRefRowMapper productRefRowMapper = new CategoryProductRefRowMapper();

  /**
   * @param jdbcTemplate runs the SQL against the configured datasource
   */
  public CategoryJdbcRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * @return {@code ACTIVE} categories, ordered by position then name
   */
  public List<Category> findAllActive() {
    LOGGER.debug("findAllActive");
    List<Category> result = jdbcTemplate.query(SELECT_ACTIVE, categoryRowMapper);
    LOGGER.debug("findAllActive -> count={}", result.size());
    return result;
  }

  /**
   * @return every category regardless of status, same order as {@link #findAllActive()}
   */
  public List<Category> findAll() {
    LOGGER.debug("findAll");
    List<Category> result = jdbcTemplate.query(SELECT_ALL, categoryRowMapper);
    LOGGER.debug("findAll -> count={}", result.size());
    return result;
  }

  /**
   * Sets each category's {@code position} to its index in {@code orderedIds}, in one batch.
   *
   * @param orderedIds every category id, in its new order
   */
  public void updatePositions(List<UUID> orderedIds) {
    LOGGER.debug("updatePositions count={}", orderedIds.size());
    List<Object[]> params = orderedIds.stream().map(id -> new Object[] {0, id}).toList();
    for (int i = 0; i < orderedIds.size(); i++) {
      params.get(i)[0] = i;
    }
    int[] updateCounts =
        jdbcTemplate.batchUpdate("UPDATE categories SET position = ? WHERE id = ?", params);
    LOGGER.debug("updatePositions -> {} rows updated", updateCounts.length);
  }

  /**
   * @param categoryId the category to count products for
   * @return number of products associated with it, regardless of status
   */
  public long countByCategory(UUID categoryId) {
    LOGGER.debug("countByCategory categoryId={}", categoryId);
    Long count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM product_categories WHERE category_id = ?",
            Long.class,
            categoryId);
    long result = count == null ? 0 : count;
    LOGGER.debug("countByCategory categoryId={} -> {}", categoryId, result);
    return result;
  }

  /**
   * {@code ACTIVE} products for which {@code categoryId} is their only {@code ACTIVE} category —
   * the ones that would disappear from the storefront if it were deactivated.
   *
   * @param categoryId the category being previewed for deactivation
   * @return the affected products
   */
  public List<CategoryProductRef> findLosingVisibility(UUID categoryId) {
    LOGGER.debug("findLosingVisibility categoryId={}", categoryId);
    String sql =
        """
        SELECT p.id, p.name, p.status
        FROM products p
        JOIN product_categories pc ON pc.product_id = p.id AND pc.category_id = ?
        WHERE p.status = 'ACTIVE'
          AND NOT EXISTS (
            SELECT 1 FROM product_categories pc2
            JOIN categories c2 ON c2.id = pc2.category_id AND c2.status = 'ACTIVE'
            WHERE pc2.product_id = p.id AND pc2.category_id <> ?
          )
        """;
    List<CategoryProductRef> result =
        jdbcTemplate.query(sql, productRefRowMapper, categoryId, categoryId);
    LOGGER.debug("findLosingVisibility categoryId={} -> count={}", categoryId, result.size());
    return result;
  }

  /**
   * Products that would be left with zero categories if {@code categoryId} is deleted.
   *
   * @param categoryId the category being previewed for deletion
   * @return the affected products
   */
  public List<CategoryProductRef> findLeftWithoutCategory(UUID categoryId) {
    LOGGER.debug("findLeftWithoutCategory categoryId={}", categoryId);
    String sql =
        """
        SELECT p.id, p.name, p.status
        FROM products p
        JOIN product_categories pc ON pc.product_id = p.id AND pc.category_id = ?
        WHERE NOT EXISTS (
          SELECT 1 FROM product_categories pc2
          WHERE pc2.product_id = p.id AND pc2.category_id <> ?
        )
        """;
    List<CategoryProductRef> result =
        jdbcTemplate.query(sql, productRefRowMapper, categoryId, categoryId);
    LOGGER.debug("findLeftWithoutCategory categoryId={} -> count={}", categoryId, result.size());
    return result;
  }
}
