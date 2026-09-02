package com.floristeriarosy.application.inventory.command;

import java.util.UUID;

/**
 * @param productId the product being written off
 * @param quantity the number of units wasted, positive — the negative sign is applied by {@code
 *     RegisterWasteService}, not by the caller
 * @param note required explanation (inventory.md, section 3.5)
 */
public record RegisterWasteCommand(UUID productId, int quantity, String note) {}
