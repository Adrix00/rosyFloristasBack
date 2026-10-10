package com.floristeriarosy.application.order.command;

import java.util.UUID;

/**
 * @param orderId the order to reject
 * @param adminUserId the admin who triggered the transition
 * @param reason why the order cannot be fulfilled
 */
public record RejectOrderCommand(UUID orderId, UUID adminUserId, String reason) {}
