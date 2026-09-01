package com.floristeriarosy.application.product.dto;

import java.util.List;

/**
 * Preview of whether a product can be physically deleted (product.md, section 6:
 * {@code ProductDeletionImpactResponse}).
 *
 * @param deletable whether the physical delete is possible
 * @param blockedBy the reasons it is not, from {@code ORDERS}, {@code STOCK_MOVEMENTS}, {@code
 *     PURCHASES}; empty when {@code deletable}
 * @param orderCount number of order lines referencing the product
 * @param stockMovementCount number of stock movements referencing the product
 * @param purchaseCount number of purchase lines referencing the product
 */
public record ProductDeletionImpact(
    boolean deletable, List<String> blockedBy, long orderCount, long stockMovementCount, long purchaseCount) {

  /** Defensively copies {@code blockedBy} (SpotBugs EI_EXPOSE_REP2). */
  public ProductDeletionImpact {
    blockedBy = List.copyOf(blockedBy);
  }
}
