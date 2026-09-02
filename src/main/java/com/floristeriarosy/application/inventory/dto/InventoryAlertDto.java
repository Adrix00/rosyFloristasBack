package com.floristeriarosy.application.inventory.dto;

import com.floristeriarosy.domain.model.inventory.InventoryAlertStatus;
import com.floristeriarosy.domain.model.inventory.InventoryAlertType;
import java.time.Instant;
import java.util.UUID;

/**
 * Read shape of an inventory alert returned by every use case in this module. Kept outside {@code
 * domain} so Controllers never touch a domain type directly (HexagonalArchitectureTest).
 *
 * @param id the identifier
 * @param type which condition was detected
 * @param productId the product it was detected on
 * @param productName the product's current name, for display
 * @param observedValue the observed number
 * @param expectedValue the number it was compared against
 * @param status the current lifecycle state
 * @param resolvedByAdminId the admin who closed it, or {@code null} — always {@code null} today
 *     (known gap, no {@code auth}/{@code admin} module yet)
 * @param resolvedAt when it was closed, or {@code null} if still {@code OPEN}
 * @param createdAt when the row was created
 */
public record InventoryAlertDto(
    UUID id,
    InventoryAlertType type,
    UUID productId,
    String productName,
    int observedValue,
    int expectedValue,
    InventoryAlertStatus status,
    UUID resolvedByAdminId,
    Instant resolvedAt,
    Instant createdAt) {}
