package com.floristeriarosy.application.category.query;

/** Accepts either a UUID or a slug (category.md, section 4). */
public record GetCategoryQuery(String idOrSlug) {}
