package com.floristeriarosy.application.product.dto;

import java.util.UUID;

/**
 * One image in a product's gallery, as embedded in {@link ProductDto}.
 *
 * @param id the {@code product_images} row id
 * @param imageId the referenced {@code images} row id
 * @param url the public CDN URL, or {@code null} — always {@code null} until image.md's builder
 *     exists (tracked gap, same as category's {@code imageUrl})
 * @param altText alt text for this use of the image, or {@code null}
 * @param position gallery order; position 0 is the main image
 */
public record ProductImageRef(UUID id, UUID imageId, String url, String altText, int position) {}
