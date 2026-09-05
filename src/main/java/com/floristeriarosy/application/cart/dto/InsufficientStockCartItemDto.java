package com.floristeriarosy.application.cart.dto;

import java.util.UUID;

/**
 * A line whose quantity exceeds real stock, blocking checkout without being adjusted (cart.md §6,
 * rule 3.4).
 *
 * @param productId the affected product's identifier
 * @param productName the affected product's name
 * @param requestedQuantity the quantity currently in the cart
 * @param availableQuantity the real stock available
 */
public record InsufficientStockCartItemDto(
    UUID productId, String productName, int requestedQuantity, int availableQuantity) {}
