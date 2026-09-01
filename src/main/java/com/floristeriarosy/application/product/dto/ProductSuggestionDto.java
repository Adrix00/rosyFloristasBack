package com.floristeriarosy.application.product.dto;

/**
 * Read shape of a product for the autocomplete dropdown (product.md, section 6: {@code
 * ProductSuggestionResponse}). Only what the dropdown needs — never a full product.
 *
 * @param name the product name
 * @param slug the product slug
 */
public record ProductSuggestionDto(String name, String slug) {}
