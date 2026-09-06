package com.floristeriarosy.infrastructure.web.request.cart;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * @param quantity required, positive, at most 99 — fixes the line's quantity, does not add to it
 *     (cart.md §5)
 */
public record UpdateCartItemRequest(@NotNull @Positive @Max(99) Integer quantity) {}
