package com.floristeriarosy.infrastructure.web.request.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * @param name required; the slug is generated from it, never supplied
 * @param description optional
 * @param imageId optional; must reference an existing {@code images} row
 * @param position optional, defaults to 0
 */
public record CreateCategoryRequest(
    @NotBlank @Size(max = 150) String name,
    @Size(max = 2000) String description,
    UUID imageId,
    @PositiveOrZero Integer position) {}
