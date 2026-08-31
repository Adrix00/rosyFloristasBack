package com.floristeriarosy.application.category.command;

import java.util.UUID;

/**
 * @param name the category name; the slug is generated from it, never supplied
 * @param description optional description
 * @param imageId optional reference to an {@code images} row
 * @param position position in the public catalog
 */
public record CreateCategoryCommand(String name, String description, UUID imageId, int position) {}
