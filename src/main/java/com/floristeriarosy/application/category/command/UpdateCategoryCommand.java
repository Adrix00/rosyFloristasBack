package com.floristeriarosy.application.category.command;

import java.util.UUID;

public record UpdateCategoryCommand(
    UUID id, String name, String description, UUID imageId, int position) {}
