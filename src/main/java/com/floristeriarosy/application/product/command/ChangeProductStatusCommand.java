package com.floristeriarosy.application.product.command;

import com.floristeriarosy.domain.model.product.ProductStatus;
import java.util.UUID;

/**
 * @param id the product to change
 * @param status the new status
 */
public record ChangeProductStatusCommand(UUID id, ProductStatus status) {}
