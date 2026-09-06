package com.floristeriarosy.infrastructure.web.response.cart;

import java.util.UUID;

/**
 * @param productId the removed product's identifier
 * @param productName the removed product's name
 */
public record RemovedCartItemResponse(UUID productId, String productName) {}
