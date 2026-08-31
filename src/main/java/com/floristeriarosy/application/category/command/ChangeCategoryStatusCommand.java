package com.floristeriarosy.application.category.command;

import com.floristeriarosy.domain.model.category.CategoryStatus;
import java.util.UUID;

/**
 * @param id the category to change
 * @param status the status to set
 */
public record ChangeCategoryStatusCommand(UUID id, CategoryStatus status) {}
