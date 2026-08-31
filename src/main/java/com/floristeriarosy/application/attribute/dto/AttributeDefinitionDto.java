package com.floristeriarosy.application.attribute.dto;

import com.floristeriarosy.domain.model.attribute.AttributeDataType;
import java.time.Instant;
import java.util.UUID;

/**
 * Read shape of an attribute definition returned by write and read use cases. Kept outside
 * {@code domain} so Controllers never touch a domain type directly (HexagonalArchitectureTest).
 *
 * @param id the identifier
 * @param attributeKey the declared key, immutable once created
 * @param label the visible label
 * @param dataType the declared value type, immutable once created
 * @param filterable whether {@code GET /products} may filter by this key
 * @param position position in the admin's attribute list
 * @param createdAt when the row was created
 * @param updatedAt when the row was last updated
 */
public record AttributeDefinitionDto(
    UUID id,
    String attributeKey,
    String label,
    AttributeDataType dataType,
    boolean filterable,
    int position,
    Instant createdAt,
    Instant updatedAt) {}
