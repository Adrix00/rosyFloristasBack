package com.floristeriarosy.application.product.query;

import com.floristeriarosy.domain.model.product.ProductStatus;

/**
 * Input of {@code GetProductsUseCase}: {@code GET /products/all} (ADMIN; product.md, section 4).
 * Unlike {@code SearchProductsQuery}, ignores visibility entirely — every status is returned.
 *
 * @param status only products with this status, or {@code null} for every status
 * @param withoutCategory whether to only return products with no category at all
 * @param isExtra only products with this {@code is_extra} flag, or {@code null} for both
 * @param page requested page, zero-based, clamped to {@code >= 0}
 * @param size requested page size, clamped to {@code [1, MAX_PAGE_SIZE]}
 */
public record GetProductsQuery(
    ProductStatus status, boolean withoutCategory, Boolean isExtra, int page, int size) {}
