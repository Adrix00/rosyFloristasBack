package com.floristeriarosy.application.product.query;

import java.util.UUID;

/**
 * @param id the product whose suggested extras to list
 */
public record GetProductExtrasQuery(UUID id) {}
