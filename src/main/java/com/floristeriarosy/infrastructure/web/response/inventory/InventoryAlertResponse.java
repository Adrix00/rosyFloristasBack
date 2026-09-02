package com.floristeriarosy.infrastructure.web.response.inventory;

import com.floristeriarosy.domain.model.inventory.InventoryAlertStatus;
import com.floristeriarosy.domain.model.inventory.InventoryAlertType;
import java.time.Instant;
import java.util.UUID;

/**
 * @param id the identifier
 * @param type which condition was detected
 * @param productId the product it was detected on
 * @param productName the product's current name
 * @param observedValue the observed number, labeled per {@code type} by the presentation layer
 * @param expectedValue the number it was compared against, labeled per {@code type}
 * @param status the current lifecycle state
 * @param resolvedByAdminName who closed it, or {@code null} — always {@code null} today (no {@code
 *     auth}/{@code admin} module yet)
 * @param resolvedAt when it was closed, or {@code null} if still {@code OPEN}
 * @param createdAt when the row was created
 */
public record InventoryAlertResponse(
    UUID id,
    InventoryAlertType type,
    UUID productId,
    String productName,
    int observedValue,
    int expectedValue,
    InventoryAlertStatus status,
    String resolvedByAdminName,
    Instant resolvedAt,
    Instant createdAt) {}
