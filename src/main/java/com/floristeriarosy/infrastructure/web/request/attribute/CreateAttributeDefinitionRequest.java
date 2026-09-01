package com.floristeriarosy.infrastructure.web.request.attribute;

import com.floristeriarosy.domain.model.attribute.AttributeDataType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * @param attributeKey required; immutable once created
 * @param label required
 * @param dataType required; immutable once created
 * @param filterable optional, defaults to {@code true}
 * @param position optional, defaults to 0
 */
public record CreateAttributeDefinitionRequest(
    @NotBlank @Size(max = 100) String attributeKey,
    @NotBlank @Size(max = 150) String label,
    @NotNull AttributeDataType dataType,
    Boolean filterable,
    @PositiveOrZero Integer position) {}
