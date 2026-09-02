package com.floristeriarosy.infrastructure.scheduler;

import com.floristeriarosy.application.inventory.port.in.GenerateInventoryAlertsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Triggers the daily inventory alert job (inventory.md, section 3.8; ADR-013), once a day at
 * 03:00 — low-traffic hours, and neither {@code LOW_STOCK} nor {@code RECONCILIATION_MISMATCH} is
 * time-critical (ADR-013: "A daily scheduled job, not a live check").
 */
@Component
public class InventoryAlertScheduler {

  private static final Logger LOGGER = LoggerFactory.getLogger(InventoryAlertScheduler.class);

  private final GenerateInventoryAlertsUseCase generateInventoryAlertsUseCase;

  /**
   * @param generateInventoryAlertsUseCase runs both detection queries and opens the missing alerts
   */
  public InventoryAlertScheduler(GenerateInventoryAlertsUseCase generateInventoryAlertsUseCase) {
    this.generateInventoryAlertsUseCase = generateInventoryAlertsUseCase;
  }

  /** Runs daily at 03:00 server time. */
  @Scheduled(cron = "0 0 3 * * *")
  public void generateAlerts() {
    LOGGER.debug("generateAlerts");
    generateInventoryAlertsUseCase.execute();
    LOGGER.debug("generateAlerts -> done");
  }
}
