package com.floristeriarosy.application.inventory.dto;

import java.util.UUID;

/**
 * One managed product at or below its configured low-stock threshold (inventory.md, section 3.8:
 * {@code LOW_STOCK}).
 *
 * @param productId the product below threshold
 * @param stock {@code products.stock}
 * @param threshold {@code products.low_stock_threshold}
 */
public record LowStockCandidate(UUID productId, int stock, int threshold) {}
