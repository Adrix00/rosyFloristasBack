package com.floristeriarosy.application.inventory.dto;

import java.util.UUID;

/**
 * One product whose stock disagrees with the sum of its own movements (inventory.md, section 3.8:
 * {@code RECONCILIATION_MISMATCH}).
 *
 * @param productId the product with the mismatch
 * @param observedStock {@code products.stock}
 * @param expectedStock the sum of {@code stock_movements.quantity} for this product
 */
public record ReconciliationMismatch(UUID productId, int observedStock, int expectedStock) {}
