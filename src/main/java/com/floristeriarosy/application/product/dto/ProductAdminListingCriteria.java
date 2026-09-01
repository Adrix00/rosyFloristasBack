package com.floristeriarosy.application.product.dto;

import com.floristeriarosy.domain.model.product.ProductStatus;

/**
 * Filter criteria for {@code ProductReadPort#findAllForAdmin} (product.md, section 4: {@code GET
 * /products/all}). Ignores visibility entirely — every status is a candidate.
 *
 * @param status only products with this status, or {@code null} for every status
 * @param withoutCategory whether to only return products with no category at all
 * @param isExtra only products with this {@code is_extra} flag, or {@code null} for both
 * @param page requested page, zero-based
 * @param size requested page size
 */
public record ProductAdminListingCriteria(
    ProductStatus status, boolean withoutCategory, Boolean isExtra, int page, int size) {}
