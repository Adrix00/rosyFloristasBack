package com.floristeriarosy.application.category.command;

import java.util.UUID;

/**
 * @param id the category to delete
 */
public record DeleteCategoryCommand(UUID id) {}
