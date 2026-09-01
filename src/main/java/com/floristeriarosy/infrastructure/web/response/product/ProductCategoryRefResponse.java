package com.floristeriarosy.infrastructure.web.response.product;

import java.util.UUID;

/**
 * @param id the category id
 * @param name the category name
 * @param slug the category slug
 */
public record ProductCategoryRefResponse(UUID id, String name, String slug) {}
