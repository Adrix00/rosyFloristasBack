package com.floristeriarosy.infrastructure.web.request.product;

import com.floristeriarosy.domain.model.product.ProductStatus;
import jakarta.validation.constraints.NotNull;

/**
 * @param status required
 */
public record ChangeProductStatusRequest(@NotNull ProductStatus status) {}
