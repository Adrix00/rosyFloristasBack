package com.floristeriarosy.infrastructure.web.response.category;

import java.util.UUID;

/**
 * @param id the product's identifier
 * @param name the product's name
 * @param status the product's status, e.g. {@code "ACTIVE"}
 */
public record CategoryProductRefResponse(UUID id, String name, String status) {}
