package com.floristeriarosy.application.cart.dto;

import java.util.List;

/**
 * Result of revalidating a cart before payment (cart.md §6, rule 3.4).
 *
 * @param valid {@code true} if nothing had to be touched
 * @param removedItems products removed for no longer being {@code ACTIVE}
 * @param insufficientStockItems products whose requested quantity exceeds real stock; checkout is
 *     blocked while this is non-empty
 */
public record CartValidationDto(
    boolean valid,
    List<RemovedCartItemDto> removedItems,
    List<InsufficientStockCartItemDto> insufficientStockItems) {

  /** Defensively copies both lists (SpotBugs EI_EXPOSE_REP2). */
  public CartValidationDto {
    removedItems = List.copyOf(removedItems);
    insufficientStockItems = List.copyOf(insufficientStockItems);
  }
}
