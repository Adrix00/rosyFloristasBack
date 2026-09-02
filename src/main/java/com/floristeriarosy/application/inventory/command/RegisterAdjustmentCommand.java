package com.floristeriarosy.application.inventory.command;

import java.util.UUID;

/**
 * @param productId the product being corrected
 * @param quantity the signed delta to apply, either sign, never zero
 * @param note required explanation (inventory.md, section 3.6)
 */
public record RegisterAdjustmentCommand(UUID productId, int quantity, String note) {}
