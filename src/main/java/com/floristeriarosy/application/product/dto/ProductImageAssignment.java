package com.floristeriarosy.application.product.dto;

import java.util.UUID;

/**
 * One image to assign to a product's gallery. Position is the item's index in the list passed to
 * {@code ProductImagePort#replaceImages} (product.md, section 5).
 *
 * @param imageId the {@code images} row to reference
 * @param altText alt text for this use of the image, or {@code null}
 */
public record ProductImageAssignment(UUID imageId, String altText) {}
