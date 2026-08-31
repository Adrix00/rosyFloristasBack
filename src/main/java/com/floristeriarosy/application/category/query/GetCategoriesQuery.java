package com.floristeriarosy.application.category.query;

/** {@code includeInactive}: {@code GET /categories/all} (ADMIN) vs {@code GET /categories}. */
public record GetCategoriesQuery(boolean includeInactive) {}
