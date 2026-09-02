package com.floristeriarosy.infrastructure.web.response.inventory;

import com.floristeriarosy.domain.model.inventory.StockMovementType;
import java.time.Instant;
import java.util.UUID;

/**
 * @param id the identifier
 * @param productId the product this movement belongs to
 * @param type the kind of movement
 * @param quantity the signed quantity
 * @param resultingStock the product's stock immediately after this movement
 * @param adminUserName who triggered it, or {@code null} for a system-generated movement or an
 *     admin who has since been deactivated — always {@code null} today (no {@code auth}/{@code
 *     admin} module yet)
 * @param note the optional note, or {@code null}
 * @param createdAt when the row was created
 */
public record StockMovementResponse(
    UUID id,
    UUID productId,
    StockMovementType type,
    int quantity,
    int resultingStock,
    String adminUserName,
    String note,
    Instant createdAt) {}
