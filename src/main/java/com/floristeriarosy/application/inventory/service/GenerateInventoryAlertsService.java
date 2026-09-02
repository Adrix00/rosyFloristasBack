package com.floristeriarosy.application.inventory.service;

import com.floristeriarosy.application.inventory.dto.LowStockCandidate;
import com.floristeriarosy.application.inventory.dto.ReconciliationMismatch;
import com.floristeriarosy.application.inventory.port.in.GenerateInventoryAlertsUseCase;
import com.floristeriarosy.application.inventory.port.out.InventoryAlertPort;
import com.floristeriarosy.application.inventory.port.out.LowStockPort;
import com.floristeriarosy.application.inventory.port.out.StockMovementReadPort;
import com.floristeriarosy.domain.model.inventory.InventoryAlert;
import com.floristeriarosy.domain.model.inventory.InventoryAlertType;
import com.floristeriarosy.domain.model.inventory.valueobject.InventoryAlertId;
import com.floristeriarosy.domain.model.product.valueobject.ProductId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Implements {@link GenerateInventoryAlertsUseCase}: the daily job (inventory.md, section 3.8;
 * ADR-013), triggered by {@code infrastructure.scheduler.InventoryAlertScheduler}. Runs both
 * detection queries and opens a new alert for every result that does not already have one open —
 * {@code ux_inventory_alerts_open} is what actually prevents a duplicate, not this method's logic.
 *
 * <p>Deliberately not {@code @Transactional} at this level: each {@link InventoryAlertPort#save}
 * call needs its own transaction so a duplicate on one product (a routine, expected outcome, not
 * an error) doesn't abort the surrounding Postgres transaction and take down every other row's
 * insert with it — a single shared transaction across the whole loop would fail the batch on the
 * first re-detected condition. No rule in inventory.md requires the batch itself to be atomic,
 * only that each alert is independently idempotent, which the unique index already guarantees.
 */
@Service
public class GenerateInventoryAlertsService implements GenerateInventoryAlertsUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(GenerateInventoryAlertsService.class);

  private final StockMovementReadPort stockMovementReadPort;
  private final LowStockPort lowStockPort;
  private final InventoryAlertPort alertPort;

  /**
   * @param stockMovementReadPort runs the {@code RECONCILIATION_MISMATCH} detection query
   * @param lowStockPort runs the {@code LOW_STOCK} detection query
   * @param alertPort inserts the missing alerts, silently skipping an already-open duplicate
   */
  public GenerateInventoryAlertsService(
      StockMovementReadPort stockMovementReadPort, LowStockPort lowStockPort, InventoryAlertPort alertPort) {
    this.stockMovementReadPort = stockMovementReadPort;
    this.lowStockPort = lowStockPort;
    this.alertPort = alertPort;
  }

  /** Runs both detection queries and opens the missing alerts. Never closes an existing one. */
  @Override
  public void execute() {
    LOGGER.debug("generateInventoryAlerts");

    List<ReconciliationMismatch> mismatches = stockMovementReadPort.findReconciliationMismatches();
    for (ReconciliationMismatch mismatch : mismatches) {
      openIfAbsent(
          InventoryAlertType.RECONCILIATION_MISMATCH,
          ProductId.of(mismatch.productId()),
          mismatch.observedStock(),
          mismatch.expectedStock());
    }

    List<LowStockCandidate> lowStockCandidates = lowStockPort.findBelowThreshold();
    for (LowStockCandidate candidate : lowStockCandidates) {
      openIfAbsent(
          InventoryAlertType.LOW_STOCK, ProductId.of(candidate.productId()), candidate.stock(), candidate.threshold());
    }

    LOGGER.debug(
        "generateInventoryAlerts -> mismatches={} lowStockCandidates={}",
        mismatches.size(),
        lowStockCandidates.size());
  }

  /**
   * @param type which condition was detected
   * @param productId the product it was detected on
   * @param observedValue the observed number
   * @param expectedValue the number it was compared against
   */
  private void openIfAbsent(InventoryAlertType type, ProductId productId, int observedValue, int expectedValue) {
    InventoryAlert alert = InventoryAlert.open(InventoryAlertId.newId(), type, productId, observedValue, expectedValue);
    boolean created = alertPort.save(alert);
    LOGGER.debug("openIfAbsent type={} productId={} -> created={}", type, productId, created);
  }
}
