package com.floristeriarosy.application.inventory.service;

import com.floristeriarosy.application.inventory.command.DismissInventoryAlertCommand;
import com.floristeriarosy.application.inventory.dto.InventoryAlertDto;
import com.floristeriarosy.application.inventory.mapper.InventoryAlertDtoMapper;
import com.floristeriarosy.application.inventory.port.in.DismissInventoryAlertUseCase;
import com.floristeriarosy.application.admin.port.out.AdminReadPort;
import com.floristeriarosy.application.inventory.port.out.InventoryAlertPort;
import com.floristeriarosy.application.product.port.out.ProductReadPort;
import com.floristeriarosy.application.shared.port.out.PiiCryptoPort;
import com.floristeriarosy.application.shared.support.AdminDisplayNameResolver;
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
 * Implements {@link DismissInventoryAlertUseCase}: closes an alert as acknowledged, no action
 * needed (inventory.md, section 3.8: "Descartar").
 */
@Service
@Transactional
public class DismissInventoryAlertService implements DismissInventoryAlertUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(DismissInventoryAlertService.class);

  private final InventoryAlertPort alertPort;
  private final ProductReadPort productReadPort;
  private final AdminReadPort adminReadPort;
  private final PiiCryptoPort piiCryptoPort;

  /**
   * @param alertPort loads and persists the alert being dismissed
   * @param productReadPort resolves the product's current name for the response
   * @param adminReadPort resolves the closing admin's encrypted email, for the response's {@code
   *     resolvedByAdminName} (inventory.md, section 6)
   * @param piiCryptoPort decrypts it (ADR-005)
   */
  public DismissInventoryAlertService(
      InventoryAlertPort alertPort,
      ProductReadPort productReadPort,
      AdminReadPort adminReadPort,
      PiiCryptoPort piiCryptoPort) {
    this.alertPort = alertPort;
    this.productReadPort = productReadPort;
    this.adminReadPort = adminReadPort;
    this.piiCryptoPort = piiCryptoPort;
  }

  /**
   * @param command the alert to close, plus an optional note
   * @return the dismissed alert
   * @throws InventoryAlertNotFoundException {@code command.id()} does not exist
   */
  @Override
  @PreAuthorize("hasRole('ADMIN')")
  public InventoryAlertDto execute(DismissInventoryAlertCommand command) {
    LOGGER.debug("dismissInventoryAlert id={} adminUserId={}", command.id(), command.adminUserId());

    InventoryAlertId id = InventoryAlertId.of(command.id());
    InventoryAlert alert =
        alertPort
            .findById(id)
            .orElseThrow(
                () -> new InventoryAlertNotFoundException("Inventory alert " + id + " not found"));

    alert.dismiss(command.adminUserId(), command.note(), Instant.now());
    InventoryAlert saved = alertPort.dismiss(alert);

    String productName =
        productReadPort.findById(saved.productId()).map(Product::name).orElse(null);
    String adminName = AdminDisplayNameResolver.resolve(adminReadPort, piiCryptoPort, saved.resolvedByAdminId());
    InventoryAlertDto result = InventoryAlertDtoMapper.toDto(saved, productName, adminName);
    LOGGER.debug("dismissInventoryAlert -> id={} status={}", result.id(), result.status());
    return result;
  }
}
