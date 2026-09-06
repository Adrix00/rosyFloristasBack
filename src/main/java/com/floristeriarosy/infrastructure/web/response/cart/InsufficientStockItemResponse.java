package com.floristeriarosy.infrastructure.web.response.cart;

import java.util.UUID;

/**
 * @param productId the affected product's identifier
 * @param productName the affected product's name
 * @param requestedQuantity the quantity currently in the cart
 * @param availableQuantity the real stock available
 */
public record InsufficientStockItemResponse(
    UUID productId, String productName, int requestedQuantity, int availableQuantity) {}
