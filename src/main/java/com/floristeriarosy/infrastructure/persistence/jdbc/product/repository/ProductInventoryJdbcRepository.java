package com.floristeriarosy.infrastructure.persistence.jdbc.product.repository;

import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC writes for a product's stock and its {@code stock_movements} trail (ADR-002, product.md
 * section 3.7). Bypasses JPA and the {@code @Version}-guarded save cycle deliberately (ADR-009):
 * these are the conditional, narrowly-scoped writes that mechanism is not for.
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
   * Activates managed inventory. Tries an {@code INITIAL} movement first; if {@code
   * ux_stock_movements_initial} already fired for this product (it was managed before and later
   * turned unmanaged), falls back to an {@code ADJUSTMENT} instead — the constraint itself decides
   * which case this is, so this method needs no history query of its own.
   *
   * @param productId the product to activate inventory for
   * @param stock the initial stock
   * @param lowStockThreshold the low-stock alert threshold, or {@code null}
   * @param note optional note for the movement
   */
  public void initializeStock(UUID productId, int stock, Integer lowStockThreshold, String note) {
    LOGGER.debug("initializeStock productId={} stock={}", productId, stock);
    try {
      insertMovement(productId, "INITIAL", stock, stock, note);
    } catch (DataIntegrityViolationException alreadyInitialized) {
      LOGGER.debug(
          "initializeStock productId={} -> ux_stock_movements_initial hit, falling back to ADJUSTMENT",
          productId);
      insertMovement(productId, "ADJUSTMENT", stock, stock, note);
    }
    updateProductStock(productId, stock, lowStockThreshold);
    LOGGER.debug("initializeStock productId={} -> activated", productId);
  }

  /**
   * Sets {@code stock} to {@code newStock} on an already-managed product, recording the delta as
   * an {@code ADJUSTMENT}. A zero delta updates only the threshold, without writing a movement row
   * ({@code chk_stock_movements_quantity_nonzero} forbids a zero-quantity non-{@code INITIAL}
   * movement).
   *
   * @param productId the product to adjust
   * @param newStock the new stock value
   * @param lowStockThreshold the low-stock alert threshold, or {@code null}
   * @param note optional note for the movement
   */
  public void adjustStock(UUID productId, int newStock, Integer lowStockThreshold, String note) {
    LOGGER.debug("adjustStock productId={} newStock={}", productId, newStock);
    Integer currentStock =
        jdbcTemplate.queryForObject("SELECT stock FROM products WHERE id = ?", Integer.class, productId);
    int delta = newStock - (currentStock == null ? 0 : currentStock);
    if (delta != 0) {
      insertMovement(productId, "ADJUSTMENT", delta, newStock, note);
    }
    updateProductStock(productId, newStock, lowStockThreshold);
    LOGGER.debug("adjustStock productId={} -> delta={}", productId, delta);
  }

  /**
   * Switches a product back to unmanaged inventory: {@code stock} becomes {@code null}. The
   * movement history is left intact.
   *
   * @param productId the product to deactivate inventory for
   */
  public void disableStockManagement(UUID productId) {
    LOGGER.debug("disableStockManagement productId={}", productId);
    jdbcTemplate.update("UPDATE products SET stock = NULL, updated_at = ? WHERE id = ?", Instant.now(), productId);
    LOGGER.debug("disableStockManagement productId={} -> disabled", productId);
  }

  /**
   * @param productId the product the movement belongs to
   * @param type {@code INITIAL} or {@code ADJUSTMENT}
   * @param quantity the movement quantity, signed
   * @param resultingStock the product's stock after this movement
   * @param note optional note
   */
  private void insertMovement(UUID productId, String type, int quantity, int resultingStock, String note) {
    jdbcTemplate.update(
        "INSERT INTO stock_movements (id, product_id, type, quantity, resulting_stock, note, created_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?)",
        UUID.randomUUID(),
        productId,
        type,
        quantity,
        resultingStock,
        note,
        Instant.now());
  }

  /**
   * @param productId the product to update
   * @param stock the new stock value
   * @param lowStockThreshold the new low-stock alert threshold, or {@code null}
   */
  private void updateProductStock(UUID productId, int stock, Integer lowStockThreshold) {
    jdbcTemplate.update(
        "UPDATE products SET stock = ?, low_stock_threshold = ?, updated_at = ? WHERE id = ?",
        stock,
        lowStockThreshold,
        Instant.now(),
        productId);
  }
}
