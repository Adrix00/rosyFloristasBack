package com.floristeriarosy.application.inventory.command;

import com.floristeriarosy.domain.model.inventory.StockMovementType;
import java.util.UUID;

/**
 * The single write path of the inventory module (inventory.md, section 1, section 7): every other
 * module ({@code product}, {@code order}, {@code purchasing}) that changes {@code products.stock}
 * goes through {@code RegisterStockMovementUseCase} with one of these.
 *
 * @param productId the product whose stock is changing
 * @param type the kind of movement
 * @param quantity the signed quantity: the caller decides the sign — negative for {@code SALE}/
 *     {@code WASTE}, positive for {@code PURCHASE}, either for {@code ADJUSTMENT}, a non-negative
 *     absolute starting value for {@code INITIAL}
 * @param adminUserId the admin who triggered it, or {@code null} for a system-generated movement
 * @param note optional note
 */
public record RegisterStockMovementCommand(
    UUID productId, StockMovementType type, int quantity, UUID adminUserId, String note) {}
