package com.floristeriarosy.application.category.dto;

import com.floristeriarosy.domain.model.category.CategoryStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Read shape of a category returned by write and read use cases. Kept outside {@code domain} so
 * Controllers never touch a domain type directly (HexagonalArchitectureTest).
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
