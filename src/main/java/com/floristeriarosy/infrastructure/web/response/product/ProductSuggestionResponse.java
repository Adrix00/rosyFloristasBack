package com.floristeriarosy.infrastructure.web.response.product;

/**
 * {@code GET /products/suggestions} item (product.md, section 6): only what a search bar's
 * autocomplete dropdown needs, never a full product.
 *
 * @param name the product name
 * @param slug the product slug
 */
public record ProductSuggestionResponse(String name, String slug) {}
