package com.floristeriarosy.infrastructure.persistence.jdbc.category.repository;

import com.floristeriarosy.application.category.dto.CategoryProductRef;
import com.floristeriarosy.domain.model.category.Category;
import com.floristeriarosy.infrastructure.persistence.jdbc.category.rowmapper.CategoryProductRefRowMapper;
import com.floristeriarosy.infrastructure.persistence.jdbc.category.rowmapper.CategoryRowMapper;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CategoryJdbcRepository {

  private static final String SELECT_ALL = "SELECT * FROM categories ORDER BY position, name";
  private static final String SELECT_ACTIVE =
      "SELECT * FROM categories WHERE status = 'ACTIVE' ORDER BY position, name";

  private final JdbcTemplate jdbcTemplate;
  private final CategoryRowMapper categoryRowMapper = new CategoryRowMapper();
  private final CategoryProductRefRowMapper productRefRowMapper = new CategoryProductRefRowMapper();

  public CategoryJdbcRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public List<Category> findAllActive() {
    return jdbcTemplate.query(SELECT_ACTIVE, categoryRowMapper);
  }

  public List<Category> findAll() {
    return jdbcTemplate.query(SELECT_ALL, categoryRowMapper);
  }

  public void updatePositions(List<UUID> orderedIds) {
    List<Object[]> params = orderedIds.stream().map(id -> new Object[] {0, id}).toList();
    for (int i = 0; i < orderedIds.size(); i++) {
      params.get(i)[0] = i;
    }
    jdbcTemplate.batchUpdate("UPDATE categories SET position = ? WHERE id = ?", params);
  }

  public long countByCategory(UUID categoryId) {
    Long count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM product_categories WHERE category_id = ?",
            Long.class,
            categoryId);
    return count == null ? 0 : count;
  }

  /** Products, ACTIVE, for which this category is their only ACTIVE category. */
  public List<CategoryProductRef> findLosingVisibility(UUID categoryId) {
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
    return jdbcTemplate.query(sql, productRefRowMapper, categoryId, categoryId);
  }

  /** Products that would be left with zero categories if this one is deleted. */
  public List<CategoryProductRef> findLeftWithoutCategory(UUID categoryId) {
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
    return jdbcTemplate.query(sql, productRefRowMapper, categoryId, categoryId);
  }
}
