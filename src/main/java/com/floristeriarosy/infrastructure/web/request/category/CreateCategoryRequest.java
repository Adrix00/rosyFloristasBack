package com.floristeriarosy.infrastructure.web.request.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateCategoryRequest(
    @NotBlank @Size(max = 150) String name,
    @Size(max = 2000) String description,
    UUID imageId,
    @PositiveOrZero Integer position) {}
