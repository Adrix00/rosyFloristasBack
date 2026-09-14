package com.floristeriarosy.infrastructure.web.request.order;

import jakarta.validation.constraints.Size;

/**
 * @param reason optional customer/admin reason, max 500 characters; required only when an admin cancels
 */
public record CancelOrderRequest(
    @Size(max = 500) String reason) {}
