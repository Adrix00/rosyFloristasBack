package com.floristeriarosy.application.category.dto;

import java.util.UUID;

/**
 * Minimal product reference for the impact preview (category.md, section 6).
 *
 * @param id the product's identifier
 * @param name the product's name
 * @param status the product's status, e.g. {@code "ACTIVE"}
 */
public record CategoryProductRef(UUID id, String name, String status) {}
