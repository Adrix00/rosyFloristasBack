package com.floristeriarosy.infrastructure.web.request.product;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * @param managed required
 * @param stock required when {@code managed}; ignored otherwise
 * @param lowStockThreshold optional; only effective when {@code managed}; {@code null} disables
 *     the low-stock alert
 * @param note optional; feeds the resulting movement
 */
public record ChangeInventoryModeRequest(
    @NotNull Boolean managed,
    @PositiveOrZero Integer stock,
    @PositiveOrZero Integer lowStockThreshold,
    @Size(max = 500) String note) {}
