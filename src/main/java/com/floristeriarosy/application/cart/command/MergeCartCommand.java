package com.floristeriarosy.application.cart.command;

import java.util.UUID;

/**
 * Input of {@code MergeCartUseCase} (cart.md, rule 3.2), invoked by {@code auth.md} during login —
 * not yet, since customer login does not exist in this branch (feature/customer).
 *
 * @param customerId the customer who just logged in
 * @param guestSessionToken the guest cart cookie value present at login time, or {@code null}/blank
 *     if none
 */
public record MergeCartCommand(UUID customerId, String guestSessionToken) {}
