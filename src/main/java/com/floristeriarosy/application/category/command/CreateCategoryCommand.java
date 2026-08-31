package com.floristeriarosy.application.category.command;

import java.util.UUID;

public record CreateCategoryCommand(String name, String description, UUID imageId, int position) {}
