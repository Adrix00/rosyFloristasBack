package com.floristeriarosy.infrastructure.web.request.category;

import com.floristeriarosy.domain.model.category.CategoryStatus;
import jakarta.validation.constraints.NotNull;

/**
 * @param status the status to set
 */
public record ChangeCategoryStatusRequest(@NotNull CategoryStatus status) {}
