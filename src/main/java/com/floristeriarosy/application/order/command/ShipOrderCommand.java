package com.floristeriarosy.application.order.command;

import java.util.UUID;

/**
 * @param orderId the delivery order to send out
 * @param adminUserId the admin who triggered the transition
 */
public record ShipOrderCommand(UUID orderId, UUID adminUserId) {}
