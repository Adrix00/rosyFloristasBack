package com.floristeriarosy.application.category.dto;

import com.floristeriarosy.domain.model.category.CategoryStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Read shape of a category returned by write and read use cases. Kept outside {@code domain} so
 * Controllers never touch a domain type directly (HexagonalArchitectureTest).
 *
 * @param id the identifier
 * @param name the category name
 * @param slug the generated slug
 * @param description the description, or {@code null}
 * @param status {@code ACTIVE} or {@code INACTIVE}
 * @param imageId the referenced {@code images} row id, or {@code null}
 * @param position position in the public catalog
 * @param createdAt when the row was created
 * @param updatedAt when the row was last updated
 */
public record CategoryDto(
    UUID id,
    String name,
    String slug,
    String description,
    CategoryStatus status,
    UUID imageId,
    int position,
    Instant createdAt,
    Instant updatedAt) {}
