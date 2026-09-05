package com.floristeriarosy.infrastructure.web.response.cart;

import java.util.List;

/**
 * @param valid {@code true} if nothing had to be touched
 * @param removedItems products removed for no longer being {@code ACTIVE}
 * @param insufficientStockItems products whose requested quantity exceeds real stock; checkout is
 *     blocked while this is non-empty
 */
public record CartValidationResponse(
    boolean valid,
    List<RemovedCartItemResponse> removedItems,
    List<InsufficientStockItemResponse> insufficientStockItems) {

  /** Defensively copies both lists (SpotBugs EI_EXPOSE_REP2). */
  public CartValidationResponse {
    removedItems = List.copyOf(removedItems);
    insufficientStockItems = List.copyOf(insufficientStockItems);
  }
}
