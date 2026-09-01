package com.floristeriarosy.infrastructure.persistence.adapter.product;

import com.floristeriarosy.application.product.port.out.ProductInventoryPort;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import com.floristeriarosy.infrastructure.persistence.jdbc.product.repository.ProductInventoryJdbcRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

/** Implements {@link ProductInventoryPort} (ADR-003) over JDBC (ADR-002), bypassing JPA for the reasons in ADR-009. */
@Repository
public class ProductInventoryPersistenceAdapter implements ProductInventoryPort {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProductInventoryPersistenceAdapter.class);

  private final ProductInventoryJdbcRepository jdbcRepository;

  /**
   * @param jdbcRepository writes {@code products.stock} and {@code stock_movements}
   */
  public ProductInventoryPersistenceAdapter(ProductInventoryJdbcRepository jdbcRepository) {
    this.jdbcRepository = jdbcRepository;
  }

  /**
   * @param id the product to activate inventory for
   * @param stock the initial stock
   * @param lowStockThreshold the low-stock alert threshold, or {@code null}
   * @param note optional note for the movement
   */
  @Override
  public void initializeStock(ProductId id, int stock, Integer lowStockThreshold, String note) {
    LOGGER.debug("initializeStock id={} stock={}", id, stock);
    jdbcRepository.initializeStock(id.value(), stock, lowStockThreshold, note);
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
    jdbcRepository.adjustStock(id.value(), newStock, lowStockThreshold, note);
    LOGGER.debug("adjustStock id={} -> adjusted", id);
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
