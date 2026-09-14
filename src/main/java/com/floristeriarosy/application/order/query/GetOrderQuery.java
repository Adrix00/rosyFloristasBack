package com.floristeriarosy.application.order.query;

import java.util.UUID;

/**
 * @param orderNumber the order to look up
 * @param customerId the requesting customer, or {@code null} for a guest
 * @param guestEmail a guest's email, required to prove ownership when {@code customerId} is
 *     {@code null} (order.md §4: "sin sesión, el número de pedido solo no basta")
 */
public record GetOrderQuery(String orderNumber, UUID customerId, String guestEmail) {}
