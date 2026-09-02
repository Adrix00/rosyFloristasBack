package com.floristeriarosy.infrastructure.web.response.inventory;

import java.util.List;

/**
 * A paginated stock movement history (inventory.md, section 4: {@code GET
 * /products/{id}/stock-movements}).
 *
 * @param items the movements on this page
 * @param totalElements the total number of matching movements across every page
 * @param page the requested page, zero-based
 * @param size the requested page size
 */
public record StockMovementPageResponse(
    List<StockMovementResponse> items, long totalElements, int page, int size) {

  /** Defensively copies {@code items} (SpotBugs EI_EXPOSE_REP2). */
  public StockMovementPageResponse {
    items = List.copyOf(items);
  }
}
