package com.floristeriarosy.infrastructure.persistence.jdbc.product.repository;

import com.floristeriarosy.application.product.dto.ProductDeletionImpact;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** JDBC reads for product (ADR-002): visibility, the active discount price and the deletion-impact counts. */
@Repository
public class ProductJdbcRepository {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProductJdbcRepository.class);

  private static final String IS_VISIBLE_SQL =
      """
      SELECT EXISTS (
        SELECT 1 FROM products p
        WHERE p.id = ? AND p.status = 'ACTIVE'
          AND EXISTS (
            SELECT 1 FROM product_categories pc
            JOIN categories c ON c.id = pc.category_id AND c.status = 'ACTIVE'
            WHERE pc.product_id = p.id
          )
      )
      """;

  private static final String ACTIVE_SALE_PRICE_SQL =
      """
      SELECT sale_price FROM product_discounts
      WHERE product_id = ?
        AND tstzrange(starts_at, ends_at, '[)') @> now()
        AND (quantity_limit IS NULL OR quantity_sold < quantity_limit)
      """;

  private static final String DELETION_IMPACT_SQL =
      """
      SELECT
        (SELECT COUNT(*) FROM order_items WHERE product_id = ?) AS order_count,
        (SELECT COUNT(*) FROM stock_movements WHERE product_id = ?) AS stock_movement_count,
        (SELECT COUNT(*) FROM purchase_items WHERE product_id = ?) AS purchase_count
      """;

  private final JdbcTemplate jdbcTemplate;

  /**
   * @param jdbcTemplate runs the SQL against the configured datasource
   */
  public ProductJdbcRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * @param id the product to check
   * @return {@code true} if {@code status = ACTIVE} and it has at least one {@code ACTIVE}
   *     category (product.md, section 3.3)
   */
  public boolean isVisible(UUID id) {
    LOGGER.debug("isVisible id={}", id);
    Boolean result = jdbcTemplate.queryForObject(IS_VISIBLE_SQL, Boolean.class, id);
    boolean visible = Boolean.TRUE.equals(result);
    LOGGER.debug("isVisible id={} -> {}", id, visible);
    return visible;
  }

  /**
   * @param id the product to price
   * @return the {@code sale_price} of its currently active discount, if any
   */
  public Optional<BigDecimal> findActiveSalePrice(UUID id) {
    LOGGER.debug("findActiveSalePrice id={}", id);
    List<BigDecimal> rows =
        jdbcTemplate.query(ACTIVE_SALE_PRICE_SQL, (rs, rowNum) -> rs.getBigDecimal("sale_price"), id);
    Optional<BigDecimal> result = rows.stream().findFirst();
    LOGGER.debug("findActiveSalePrice id={} -> present={}", id, result.isPresent());
    return result;
  }

  /**
   * @param id the product being previewed for deletion
   * @return the impact preview
   */
  public ProductDeletionImpact deletionImpact(UUID id) {
    LOGGER.debug("deletionImpact id={}", id);
    ProductDeletionImpact result =
        jdbcTemplate.queryForObject(
            DELETION_IMPACT_SQL,
            (rs, rowNum) -> {
              long orderCount = rs.getLong("order_count");
              long stockMovementCount = rs.getLong("stock_movement_count");
              long purchaseCount = rs.getLong("purchase_count");
              List<String> blockedBy = new ArrayList<>();
              if (orderCount > 0) {
                blockedBy.add("ORDERS");
              }
              if (stockMovementCount > 0) {
                blockedBy.add("STOCK_MOVEMENTS");
              }
              if (purchaseCount > 0) {
                blockedBy.add("PURCHASES");
              }
              return new ProductDeletionImpact(
                  blockedBy.isEmpty(), blockedBy, orderCount, stockMovementCount, purchaseCount);
            },
            id,
            id,
            id);
    LOGGER.debug("deletionImpact id={} -> deletable={}", id, result.deletable());
    return result;
  }
}
