package com.floristeriarosy.infrastructure.persistence.adapter.product;

import com.floristeriarosy.application.inventory.port.in.RegisterStockMovementUseCase;
import com.floristeriarosy.application.product.port.out.ProductInventoryPort;
import com.floristeriarosy.domain.exception.inventory.InventoryAlreadyInitializedException;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import com.floristeriarosy.infrastructure.persistence.jdbc.product.repository.ProductInventoryJdbcRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

/**
 * Implements {@link ProductInventoryPort} (ADR-003). Delegates the actual {@code
 * stock_movements}/{@code products.stock} write to {@code inventory}'s {@link
 * RegisterStockMovementUseCase} — product.md, section 8: "{@code product} no escribe en {@code
 * stock_movements} por su cuenta". Only {@code low_stock_threshold} and the unconditional
 * deactivation stay product's own JDBC write ({@link ProductInventoryJdbcRepository}).
 */
@Repository
public class ProductInventoryPersistenceAdapter implements ProductInventoryPort {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProductInventoryPersistenceAdapter.class);

  private final RegisterStockMovementUseCase registerStockMovementUseCase;
  private final ProductInventoryJdbcRepository jdbcRepository;

  /**
   * @param registerStockMovementUseCase inventory's single transactional write path
   * @param jdbcRepository writes {@code products.low_stock_threshold} and the deactivation
   */
  public ProductInventoryPersistenceAdapter(
      RegisterStockMovementUseCase registerStockMovementUseCase, ProductInventoryJdbcRepository jdbcRepository) {
    this.registerStockMovementUseCase = registerStockMovementUseCase;
    this.jdbcRepository = jdbcRepository;
  }

  /**
   * Delegates the {@code INITIAL}-versus-reactivation decision to {@code inventory} itself, which
   * settles it by reading the product's movement history. This adapter used to attempt an {@code
   * INITIAL} and catch {@link InventoryAlreadyInitializedException} to fall back — but that
   * violation aborts the enclosing PostgreSQL transaction and marks it rollback-only, so the
   * fallback ran inside a transaction that could never commit and every reactivation answered 500.
   *
   * @param id the product to activate inventory for
   * @param stock the initial stock
   * @param lowStockThreshold the low-stock alert threshold, or {@code null}
   * @param note optional note for the movement
   */
  @Override
  public void initializeStock(ProductId id, int stock, Integer lowStockThreshold, String note) {
    LOGGER.debug("initializeStock id={} stock={}", id, stock);
    registerStockMovementUseCase.initializeOrReactivate(id.value(), stock, null, note);
    jdbcRepository.updateLowStockThreshold(id.value(), lowStockThreshold);
    LOGGER.debug("initializeStock id={} -> activated", id);
  }

  /**
   * Hands {@code inventory} the stock the caller already read instead of reading it again here:
   * the read and the write then live in the same conditional statement, closing the window where
   * two concurrent adjustments each computed a delta from the same stale value and compounded.
   *
   * @param id the product to adjust
   * @param expectedStock the stock the caller read before deciding
   * @param newStock the new stock value
   * @param lowStockThreshold the low-stock alert threshold, or {@code null}
   * @param note optional note for the movement
   */
  @Override
  public void adjustStock(ProductId id, int expectedStock, int newStock, Integer lowStockThreshold, String note) {
    LOGGER.debug("adjustStock id={} expectedStock={} newStock={}", id, expectedStock, newStock);
    registerStockMovementUseCase.adjustToAbsolute(id.value(), expectedStock, newStock, null, note);
    jdbcRepository.updateLowStockThreshold(id.value(), lowStockThreshold);
    LOGGER.debug("adjustStock id={} -> newStock={}", id, newStock);
  }

  /**
   * @param id the product to deactivate inventory for
   */
  @Override
  public void disableStockManagement(ProductId id) {
    LOGGER.debug("disableStockManagement id={}", id);
    jdbcRepository.disableStockManagement(id.value());
    LOGGER.debug("disableStockManagement id={} -> disabled", id);
  }
}
