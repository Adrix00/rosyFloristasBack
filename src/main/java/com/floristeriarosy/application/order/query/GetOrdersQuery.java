package com.floristeriarosy.application.order.query;

import java.util.UUID;

/**
 * @param customerId the authenticated customer whose orders to list
 */
public record GetOrdersQuery(UUID customerId) {}
