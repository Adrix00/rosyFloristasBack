package com.floristeriarosy.infrastructure.web.request.inventory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * The negative sign is applied by the service, never sent by the client (inventory.md, section 5).
 *
 * @param quantity required, positive; the amount wasted
 * @param note required explanation
 */
public record RegisterWasteRequest(
    @NotNull @Positive Integer quantity, @NotBlank @Size(max = 500) String note) {}
