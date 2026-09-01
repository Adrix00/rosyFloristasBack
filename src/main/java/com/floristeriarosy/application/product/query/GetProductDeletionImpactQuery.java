package com.floristeriarosy.application.product.query;

import java.util.UUID;

/**
 * @param id the product being previewed for deletion
 */
public record GetProductDeletionImpactQuery(UUID id) {}
