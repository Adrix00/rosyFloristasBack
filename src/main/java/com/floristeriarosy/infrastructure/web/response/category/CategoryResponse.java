package com.floristeriarosy.infrastructure.web.response.category;

import com.floristeriarosy.domain.model.category.CategoryStatus;
import java.time.Instant;
import java.util.UUID;

public record CategoryResponse(
    UUID id,
    String name,
    String slug,
    String description,
    CategoryStatus status,
    String imageUrl,
    int position,
    Instant createdAt,
    Instant updatedAt) {}
