package com.floristeriarosy.application.product.dto;

import java.util.UUID;

/**
 * One category a product belongs to, as embedded in {@link ProductDto}.
 *
 * @param id the category id
 * @param name the category name
 * @param slug the category slug
 */
public record ProductCategoryRef(UUID id, String name, String slug) {}
