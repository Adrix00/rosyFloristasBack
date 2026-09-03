package com.floristeriarosy.application.inventory.service;

import com.floristeriarosy.application.inventory.command.ResolveInventoryAlertCommand;
import com.floristeriarosy.application.inventory.dto.InventoryAlertDto;
import com.floristeriarosy.application.inventory.mapper.InventoryAlertDtoMapper;
import com.floristeriarosy.application.inventory.port.in.ResolveInventoryAlertUseCase;
import com.floristeriarosy.application.inventory.port.out.InventoryAlertPort;
import com.floristeriarosy.application.product.port.out.ProductReadPort;
import com.floristeriarosy.domain.exception.inventory.InventoryAlertNotFoundException;
import com.floristeriarosy.domain.model.inventory.InventoryAlert;
import com.floristeriarosy.domain.model.inventory.valueobject.InventoryAlertId;
import com.floristeriarosy.domain.model.product.Product;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements {@link ResolveInventoryAlertUseCase}: closes an alert as fixed (inventory.md, section
 * 3.8: "Resolver").
 *
 * <p>{@code resolvedByAdminId} is always {@code null}: no {@code auth}/{@code admin} module exists
 * yet to resolve a principal from (known gap, dev-plan.md, pending {@code feature/auth}).
 */
@Service
@Transactional
public class ResolveInventoryAlertService implements ResolveInventoryAlertUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(ResolveInventoryAlertService.class);

  private final InventoryAlertPort alertPort;
  private final ProductReadPort productReadPort;

  /**
   * @param alertPort loads and persists the alert being resolved
   * @param productReadPort resolves the product's current name for the response
   */
  public ResolveInventoryAlertService(
      InventoryAlertPort alertPort, ProductReadPort productReadPort) {
    this.alertPort = alertPort;
    this.productReadPort = productReadPort;
  }

  /**
   * @param command the alert to close, plus an optional note
   * @return the resolved alert
   * @throws InventoryAlertNotFoundException {@code command.id()} does not exist
   */
  @Override
  @PreAuthorize("hasRole('ADMIN')")
  public InventoryAlertDto execute(ResolveInventoryAlertCommand command) {
    LOGGER.debug("resolveInventoryAlert id={}", command.id());

    InventoryAlertId id = InventoryAlertId.of(command.id());
    InventoryAlert alert =
        alertPort
            .findById(id)
            .orElseThrow(
                () -> new InventoryAlertNotFoundException("Inventory alert " + id + " not found"));

    alert.resolve(null, command.note(), Instant.now());
    InventoryAlert saved = alertPort.resolve(alert);

    String productName =
        productReadPort.findById(saved.productId()).map(Product::name).orElse(null);
    InventoryAlertDto result = InventoryAlertDtoMapper.toDto(saved, productName);
    LOGGER.debug("resolveInventoryAlert -> id={} status={}", result.id(), result.status());
    return result;
  }
}
