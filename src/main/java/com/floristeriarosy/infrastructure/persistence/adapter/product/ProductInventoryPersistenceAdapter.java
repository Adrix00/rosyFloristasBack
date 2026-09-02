package com.floristeriarosy.infrastructure.persistence.adapter.product;

import com.floristeriarosy.application.inventory.port.in.RegisterStockMovementUseCase;
import com.floristeriarosy.application.product.port.out.ProductInventoryPort;
import com.floristeriarosy.domain.exception.inventory.InventoryAlreadyInitializedException;
import com.floristeriarosy.domain.model.inventory.StockMovementType;
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
   * Tries an {@code INITIAL} movement first; if {@code ux_stock_movements_initial} already fired
   * for this product (it was managed before and later turned unmanaged), falls back to an {@code
   * ADJUSTMENT} instead — inventory.md's own constraint decides which case this is, so this method
   * needs no history query of its own.
   *
   * @param id the product to activate inventory for
   * @param stock the initial stock
   * @param lowStockThreshold the low-stock alert threshold, or {@code null}
   * @param note optional note for the movement
   */
  @Override
  public void initializeStock(ProductId id, int stock, Integer lowStockThreshold, String note) {
    LOGGER.debug("initializeStock id={} stock={}", id, stock);
    try {
      registerStockMovementUseCase.execute(id.value(), StockMovementType.INITIAL, stock, null, note);
    } catch (InventoryAlreadyInitializedException alreadyInitialized) {
      LOGGER.debug("initializeStock id={} -> already initialized, falling back to ADJUSTMENT", id);
      registerStockMovementUseCase.reactivate(id.value(), stock, null, note);
    }
    jdbcRepository.updateLowStockThreshold(id.value(), lowStockThreshold);
    LOGGER.debug("initializeStock id={} -> activated", id);
  }

  /**
   * @param id the product to adjust
   * @param newStock the new stock value
   * @param lowStockThreshold the low-stock alert threshold, or {@code null}
   * @param note optional note for the movement
   */
  @Override
  public void adjustStock(ProductId id, int newStock, Integer lowStockThreshold, String note) {
    LOGGER.debug("adjustStock id={} newStock={}", id, newStock);
    Integer currentStock = jdbcRepository.currentStock(id.value());
    int delta = newStock - (currentStock == null ? 0 : currentStock);
    if (delta != 0) {
      registerStockMovementUseCase.execute(id.value(), StockMovementType.ADJUSTMENT, delta, null, note);
    }
    jdbcRepository.updateLowStockThreshold(id.value(), lowStockThreshold);
    LOGGER.debug("adjustStock id={} -> delta={}", id, delta);
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
