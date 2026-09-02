package com.floristeriarosy.application.inventory.port.in;

import com.floristeriarosy.application.inventory.dto.InventoryAlertDto;
import com.floristeriarosy.application.inventory.query.GetInventoryAlertsQuery;
import com.floristeriarosy.application.product.dto.PageResult;

/** Lists inventory alerts, paginated and filtered (inventory.md, section 4: {@code GET /inventory/alerts}). */
public interface GetInventoryAlertsUseCase {

  /**
   * @param query the combinable filters and the requested page
   * @return the matching alerts, paginated, most recent first
   */
  PageResult<InventoryAlertDto> execute(GetInventoryAlertsQuery query);
}
