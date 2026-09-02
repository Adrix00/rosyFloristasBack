package com.floristeriarosy.infrastructure.web.response.inventory;

import java.util.List;

/**
 * A paginated inventory alert history (inventory.md, section 4: {@code GET /inventory/alerts}).
 *
 * @param items the alerts on this page
 * @param totalElements the total number of matching alerts across every page
 * @param page the requested page, zero-based
 * @param size the requested page size
 */
public record InventoryAlertPageResponse(
    List<InventoryAlertResponse> items, long totalElements, int page, int size) {

  /** Defensively copies {@code items} (SpotBugs EI_EXPOSE_REP2). */
  public InventoryAlertPageResponse {
    items = List.copyOf(items);
  }
}
