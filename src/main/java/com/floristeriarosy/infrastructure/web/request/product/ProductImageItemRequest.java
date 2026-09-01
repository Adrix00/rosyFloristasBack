package com.floristeriarosy.infrastructure.web.request.product;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * One image in a {@link UpdateProductImagesRequest}. Its index in that list becomes its {@code
 * position}.
 *
 * @param imageId required; must reference an existing {@code images} row
 * @param altText optional
 */
public record ProductImageItemRequest(@NotNull UUID imageId, @Size(max = 300) String altText) {}
