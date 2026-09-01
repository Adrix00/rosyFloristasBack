package com.floristeriarosy.application.product.query;

/**
 * @param idOrSlug the raw path segment: a UUID or a slug
 */
public record GetProductQuery(String idOrSlug) {}
