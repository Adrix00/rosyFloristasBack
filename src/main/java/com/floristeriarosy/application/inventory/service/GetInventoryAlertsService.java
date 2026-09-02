package com.floristeriarosy.application.inventory.service;

import com.floristeriarosy.application.inventory.dto.InventoryAlertCriteria;
import com.floristeriarosy.application.inventory.dto.InventoryAlertDto;
import com.floristeriarosy.application.inventory.port.in.GetInventoryAlertsUseCase;
import com.floristeriarosy.application.inventory.port.out.InventoryAlertPort;
import com.floristeriarosy.application.inventory.query.GetInventoryAlertsQuery;
import com.floristeriarosy.application.product.dto.PageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Implements {@link GetInventoryAlertsUseCase}: the admin alert listing, filtered and paginated. */
@Service
public class GetInventoryAlertsService implements GetInventoryAlertsUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(GetInventoryAlertsService.class);

  private final InventoryAlertPort alertPort;

  /**
   * @param alertPort lists alerts for the admin panel
   */
  public GetInventoryAlertsService(InventoryAlertPort alertPort) {
    this.alertPort = alertPort;
  }

  /**
   * @param query the combinable filters and the requested page
   * @return the matching alerts, paginated, most recent first
   */
  @Override
  public PageResult<InventoryAlertDto> execute(GetInventoryAlertsQuery query) {
    LOGGER.debug(
        "getInventoryAlerts type={} status={} productId={} page={} size={}",
        query.type(),
        query.status(),
        query.productId(),
        query.page(),
        query.size());

    InventoryAlertCriteria criteria =
        new InventoryAlertCriteria(query.type(), query.status(), query.productId(), query.page(), query.size());
    PageResult<InventoryAlertDto> result = alertPort.findAll(criteria);

    LOGGER.debug("getInventoryAlerts -> totalElements={}", result.totalElements());
    return result;
  }
}
