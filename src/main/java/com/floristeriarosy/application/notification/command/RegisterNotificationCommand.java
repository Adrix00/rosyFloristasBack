package com.floristeriarosy.application.notification.command;

import java.util.UUID;

/**
 * @param type one of the nine catalog types (notification.md, rule 3.1) — this slice only ever
 *     sends {@code ORDER_CONFIRMED}/{@code STAFF_NEW_ORDER}, but the column accepts any of them
 * @param orderId the referenced order, or {@code null} if this notification is not order-related
 * @param customerId the referenced customer, or {@code null}
 */
public record RegisterNotificationCommand(String type, UUID orderId, UUID customerId) {}
