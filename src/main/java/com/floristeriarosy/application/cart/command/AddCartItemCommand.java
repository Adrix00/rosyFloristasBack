package com.floristeriarosy.application.cart.command;

import java.util.UUID;

/**
 * @param customerId the authenticated customer, or {@code null} for a guest (cart.md, rule 3.1)
 * @param sessionToken the guest cart cookie value, ignored when {@code customerId} is present
 * @param productId the product to add
 * @param quantity the quantity to add to the line, summed with any existing quantity (rule 3.3)
 */
public record AddCartItemCommand(UUID customerId, String sessionToken, UUID productId, int quantity) {}
