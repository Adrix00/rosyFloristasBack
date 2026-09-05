package com.floristeriarosy.infrastructure.web.request.cart;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

/**
 * @param productId required; must reference a visible product (cart.md §5, rule 3.6)
 * @param quantity required, positive, at most 99 — summed with any existing quantity (rule 3.3)
 */
public record AddCartItemRequest(
    @NotNull UUID productId, @NotNull @Positive @Max(99) Integer quantity) {}
