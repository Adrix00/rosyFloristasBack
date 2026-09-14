package com.floristeriarosy.application.order.command;

import java.util.UUID;

/**
 * @param orderId the order to cancel
 * @param customerId the caller, when a customer is cancelling their own order, or {@code null} for
 *     an admin call
 * @param adminUserId the caller, when an admin is cancelling, or {@code null} for a customer call
 * @param reason the admin's stated reason, or {@code null} for a customer-initiated cancel
 */
public record CancelOrderCommand(
    UUID orderId, UUID customerId, UUID adminUserId, String reason) {}
