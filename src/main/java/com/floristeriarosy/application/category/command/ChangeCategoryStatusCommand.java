package com.floristeriarosy.application.category.command;

import com.floristeriarosy.domain.model.category.CategoryStatus;
import java.util.UUID;

public record ChangeCategoryStatusCommand(UUID id, CategoryStatus status) {}
