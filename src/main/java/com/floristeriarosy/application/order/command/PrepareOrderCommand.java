package com.floristeriarosy.application.order.command;

import java.util.UUID;

/**
 * @param orderId the order to prepare
 * @param adminUserId the admin who triggered the transition
 */
public record PrepareOrderCommand(UUID orderId, UUID adminUserId) {}
