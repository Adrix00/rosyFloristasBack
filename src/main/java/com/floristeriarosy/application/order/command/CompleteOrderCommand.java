package com.floristeriarosy.application.order.command;

import java.util.UUID;

/**
 * @param orderId the order to mark as delivered or picked up
 * @param adminUserId the admin who triggered the transition
 */
public record CompleteOrderCommand(UUID orderId, UUID adminUserId) {}
