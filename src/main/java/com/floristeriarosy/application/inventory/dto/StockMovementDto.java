package com.floristeriarosy.application.inventory.dto;

import com.floristeriarosy.domain.model.inventory.StockMovementType;
import java.time.Instant;
import java.util.UUID;

/**
 * Read shape of a stock movement returned by every use case in this module. Kept outside {@code
 * domain} so Controllers never touch a domain type directly (HexagonalArchitectureTest).
 *
 * @param id the identifier
 * @param productId the product this movement belongs to
 * @param type the kind of movement
 * @param quantity the signed quantity
 * @param resultingStock the product's stock immediately after this movement
 * @param adminUserId the admin who triggered it, or {@code null} for a system-generated movement
 * @param adminUserName the admin's email, resolved separately from {@code adminUserId} (ADR-005:
 *     decrypted only for display, never logged) — {@code null} when {@code adminUserId} is
 * @param note the optional note, or {@code null}
 * @param createdAt when the row was created
 */
public record StockMovementDto(
    UUID id,
    UUID productId,
    StockMovementType type,
    int quantity,
    int resultingStock,
    UUID adminUserId,
    String adminUserName,
    String note,
    Instant createdAt) {}
