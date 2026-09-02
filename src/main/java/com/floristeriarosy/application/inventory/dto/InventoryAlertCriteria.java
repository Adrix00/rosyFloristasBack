package com.floristeriarosy.application.inventory.dto;

import com.floristeriarosy.domain.model.inventory.InventoryAlertStatus;
import com.floristeriarosy.domain.model.inventory.InventoryAlertType;
import java.util.UUID;

/**
 * Filter criteria for {@code InventoryAlertPort#findAll} (inventory.md, section 4: {@code GET
 * /inventory/alerts}).
 *
 * @param type only alerts of this type, or {@code null} for every type
 * @param status only alerts with this status, or {@code null} for every status
 * @param productId only alerts for this product, or {@code null} for every product
 * @param page requested page, zero-based
 * @param size requested page size
 */
public record InventoryAlertCriteria(
    InventoryAlertType type, InventoryAlertStatus status, UUID productId, int page, int size) {}
