package com.floristeriarosy.application.inventory.query;

import java.util.UUID;

/**
 * @param productId the product whose stock movement history to list
 * @param page requested page, zero-based
 * @param size requested page size
 */
public record GetStockMovementsQuery(UUID productId, int page, int size) {}
