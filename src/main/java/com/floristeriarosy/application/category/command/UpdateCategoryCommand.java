package com.floristeriarosy.application.category.command;

import java.util.UUID;

/**
 * @param id the category to update
 * @param name the new name; the slug is regenerated from it
 * @param description the new description, or {@code null} to clear it
 * @param imageId the new image reference, or {@code null} to clear it
 * @param position the new position
 */
public record UpdateCategoryCommand(
    UUID id, String name, String description, UUID imageId, int position) {}
