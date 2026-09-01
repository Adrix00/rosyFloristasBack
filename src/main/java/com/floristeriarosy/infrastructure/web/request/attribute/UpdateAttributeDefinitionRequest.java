package com.floristeriarosy.infrastructure.web.request.attribute;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * {@code attributeKey} and {@code dataType} are not here — both are immutable once created
 * (product.md, section 3.5).
 *
 * @param label required
 * @param filterable optional, defaults to {@code true}
 * @param position optional, defaults to 0
 */
public record UpdateAttributeDefinitionRequest(
    @NotBlank @Size(max = 150) String label, Boolean filterable, @PositiveOrZero Integer position) {}
