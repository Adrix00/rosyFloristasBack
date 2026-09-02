package com.floristeriarosy.infrastructure.persistence.jdbc.product.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC writes for a product's own inventory-mode fields (ADR-002, product.md section 3.7). The
 * {@code stock_movements} write path itself moved to {@code inventory}'s {@code
 * ProductStockPort}/{@code RegisterStockMovementUseCase} (inventory.md, section 1): this repository
 * now only covers what stays product's own concern — the {@code low_stock_threshold} setting, a
 * diagnostic read to compute an adjustment delta, and the unconditional deactivation.
 */
@Repository
public class ProductInventoryJdbcRepository {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProductInventoryJdbcRepository.class);

  private final JdbcTemplate jdbcTemplate;

  /**
   * @param jdbcTemplate runs the SQL against the configured datasource
   */
  public ProductInventoryJdbcRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * Diagnostic-only read (not the hot sale path inventory.md section 3.1 protects): an admin-driven
   * recount needs the current value to compute the delta {@code RegisterStockMovementUseCase}
   * expects.
   *
   * @param productId the product to read
   * @return the current {@code stock}, or {@code null} if unmanaged
   */
  public Integer currentStock(UUID productId) {
    LOGGER.debug("currentStock productId={}", productId);
    Integer result = jdbcTemplate.queryForObject("SELECT stock FROM products WHERE id = ?", Integer.class, productId);
    LOGGER.debug("currentStock productId={} -> {}", productId, result);
    return result;
  }

  /**
   * @param productId the product to update
   * @param lowStockThreshold the new low-stock alert threshold, or {@code null}
   */
  public void updateLowStockThreshold(UUID productId, Integer lowStockThreshold) {
    LOGGER.debug("updateLowStockThreshold productId={} lowStockThreshold={}", productId, lowStockThreshold);
    jdbcTemplate.update(
        "UPDATE products SET low_stock_threshold = ?, updated_at = ? WHERE id = ?",
        lowStockThreshold,
        Timestamp.from(Instant.now()),
        productId);
    LOGGER.debug("updateLowStockThreshold productId={} -> updated", productId);
  }

  /**
   * Switches a product back to unmanaged inventory: {@code stock} becomes {@code null}. The
   * movement history is left intact.
   *
   * @param productId the product to deactivate inventory for
   */
  public void disableStockManagement(UUID productId) {
    LOGGER.debug("disableStockManagement productId={}", productId);
    jdbcTemplate.update(
        "UPDATE products SET stock = NULL, updated_at = ? WHERE id = ?",
        Timestamp.from(Instant.now()),
        productId);
    LOGGER.debug("disableStockManagement productId={} -> disabled", productId);
  }
}
