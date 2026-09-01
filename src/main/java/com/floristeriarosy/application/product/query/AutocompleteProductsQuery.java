package com.floristeriarosy.application.product.query;

/**
 * Input of {@code AutocompleteProductsUseCase}: {@code GET /products/suggestions} (product.md,
 * section 4). Trigram autocomplete, not full-text — tolerant of prefixes and missing accents
 * (ADR-006).
 *
 * @param q the raw text typed so far
 */
public record AutocompleteProductsQuery(String q) {}
