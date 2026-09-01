package com.floristeriarosy.application.product.command;

import java.util.UUID;

/**
 * @param id the product to change
 * @param managed whether inventory should be managed after this call
 * @param stock the stock to set; required when {@code managed}, ignored otherwise
 * @param lowStockThreshold the low-stock alert threshold, or {@code null} to leave it unset;
 *     ignored when {@code !managed}
 * @param note optional note for the resulting movement
 */
public record ChangeInventoryModeCommand(
    UUID id, boolean managed, Integer stock, Integer lowStockThreshold, String note) {}
