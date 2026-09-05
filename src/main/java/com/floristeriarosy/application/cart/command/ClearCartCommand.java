package com.floristeriarosy.application.cart.command;

import java.util.UUID;

/**
 * @param customerId the authenticated customer, or {@code null} for a guest (cart.md, rule 3.1)
 * @param sessionToken the guest cart cookie value, ignored when {@code customerId} is present
 */
public record ClearCartCommand(UUID customerId, String sessionToken) {}
