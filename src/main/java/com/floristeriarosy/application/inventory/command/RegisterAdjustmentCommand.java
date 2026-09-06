package com.floristeriarosy.application.inventory.command;

import java.util.UUID;

/**
 * @param productId the product being corrected
 * @param quantity the signed delta to apply, either sign, never zero
 * @param note required explanation (inventory.md, section 3.6)
 * @param adminUserId the admin who triggered it, resolved from the caller's JWT
 */
public record RegisterAdjustmentCommand(UUID productId, int quantity, String note, UUID adminUserId) {}
