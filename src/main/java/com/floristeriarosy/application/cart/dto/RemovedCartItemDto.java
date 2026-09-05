package com.floristeriarosy.application.cart.dto;

import java.util.UUID;

/**
 * A line automatically removed by validation because its product is no longer {@code ACTIVE}
 * (cart.md §6, rule 3.4).
 *
 * @param productId the removed product's identifier
 * @param productName the removed product's name
 */
public record RemovedCartItemDto(UUID productId, String productName) {}
