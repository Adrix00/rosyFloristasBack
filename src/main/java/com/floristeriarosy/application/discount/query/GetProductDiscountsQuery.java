package com.floristeriarosy.application.discount.query;

import java.util.UUID;

/**
 * @param productId the product whose discount history to list
 */
public record GetProductDiscountsQuery(UUID productId) {}
