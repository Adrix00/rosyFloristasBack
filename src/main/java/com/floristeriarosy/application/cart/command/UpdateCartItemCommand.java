package com.floristeriarosy.application.cart.command;

import java.util.UUID;

/**
 * @param customerId the authenticated customer, or {@code null} for a guest (cart.md, rule 3.1)
 * @param sessionToken the guest cart cookie value, ignored when {@code customerId} is present
 * @param productId the product whose line to fix
 * @param quantity the exact quantity to set, replacing the current one (cart.md §5)
 */
public record UpdateCartItemCommand(UUID customerId, String sessionToken, UUID productId, int quantity) {}
