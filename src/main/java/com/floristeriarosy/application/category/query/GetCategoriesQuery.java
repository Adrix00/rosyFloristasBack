package com.floristeriarosy.application.category.query;

/**
 * @param includeInactive {@code true} for {@code GET /categories/all} (ADMIN), {@code false} for
 *     {@code GET /categories} (public)
 */
public record GetCategoriesQuery(boolean includeInactive) {}
