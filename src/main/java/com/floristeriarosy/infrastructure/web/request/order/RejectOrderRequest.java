package com.floristeriarosy.infrastructure.web.request.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param reason why the order cannot be fulfilled — required, max 500 characters
 */
public record RejectOrderRequest(
    @NotBlank @Size(max = 500) String reason) {}
