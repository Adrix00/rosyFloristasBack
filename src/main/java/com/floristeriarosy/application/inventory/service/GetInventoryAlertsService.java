package com.floristeriarosy.application.inventory.service;

import com.floristeriarosy.application.admin.port.out.AdminReadPort;
import com.floristeriarosy.application.inventory.dto.InventoryAlertCriteria;
import com.floristeriarosy.application.inventory.dto.InventoryAlertDto;
import com.floristeriarosy.application.inventory.port.in.GetInventoryAlertsUseCase;
import com.floristeriarosy.application.inventory.port.out.InventoryAlertPort;
import com.floristeriarosy.application.inventory.query.GetInventoryAlertsQuery;
import com.floristeriarosy.application.product.dto.PageResult;
import com.floristeriarosy.application.shared.port.out.PiiCryptoPort;
import com.floristeriarosy.application.shared.support.AdminDisplayNameResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Implements {@link GetInventoryAlertsUseCase}: the admin alert listing, filtered and paginated.
 */
@Service
public class GetInventoryAlertsService implements GetInventoryAlertsUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(GetInventoryAlertsService.class);

  private final InventoryAlertPort alertPort;
  private final AdminReadPort adminReadPort;
  private final PiiCryptoPort piiCryptoPort;

  /**
   * @param alertPort lists alerts for the admin panel
   * @param adminReadPort resolves a closing admin's encrypted email, for {@code
   *     resolvedByAdminName} (inventory.md, section 6)
   * @param piiCryptoPort decrypts it (ADR-005)
   */
  public GetInventoryAlertsService(
      InventoryAlertPort alertPort, AdminReadPort adminReadPort, PiiCryptoPort piiCryptoPort) {
    this.alertPort = alertPort;
    this.adminReadPort = adminReadPort;
    this.piiCryptoPort = piiCryptoPort;
  }

  /**
   * @param query the combinable filters and the requested page
   * @return the matching alerts, paginated, most recent first
   */
  @Override
  @PreAuthorize("hasRole('ADMIN')")
  public PageResult<InventoryAlertDto> execute(GetInventoryAlertsQuery query) {
    LOGGER.debug(
        "getInventoryAlerts type={} status={} productId={} page={} size={}",
        query.type(),
        query.status(),
        query.productId(),
        query.page(),
        query.size());

    InventoryAlertCriteria criteria =
        new InventoryAlertCriteria(
            query.type(), query.status(), query.productId(), query.page(), query.size());
    PageResult<InventoryAlertDto> result = alertPort.findAll(criteria);
    PageResult<InventoryAlertDto> withNames =
        new PageResult<>(
            result.items().stream().map(this::withResolvedByAdminName).toList(),
            result.totalElements(),
            result.page(),
            result.size());

    LOGGER.debug("getInventoryAlerts -> totalElements={}", withNames.totalElements());
    return withNames;
  }

  /**
   * @param dto an alert fetched from persistence, with {@code resolvedByAdminName} always {@code
   *     null} (the row mapper cannot decrypt PII)
   * @return the same alert with {@code resolvedByAdminName} resolved, if it has a resolver
   */
  private InventoryAlertDto withResolvedByAdminName(InventoryAlertDto dto) {
    if (dto.resolvedByAdminId() == null) {
      return dto;
    }
    String adminName = AdminDisplayNameResolver.resolve(adminReadPort, piiCryptoPort, dto.resolvedByAdminId());
    return new InventoryAlertDto(
        dto.id(),
        dto.type(),
        dto.productId(),
        dto.productName(),
        dto.observedValue(),
        dto.expectedValue(),
        dto.status(),
        dto.resolvedByAdminId(),
        adminName,
        dto.resolvedAt(),
        dto.createdAt());
  }
}
