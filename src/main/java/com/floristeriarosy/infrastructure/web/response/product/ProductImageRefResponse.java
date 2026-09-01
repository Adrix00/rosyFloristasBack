package com.floristeriarosy.infrastructure.web.response.product;

import java.util.UUID;

/**
 * @param id the {@code product_images} row id
 * @param url the public CDN URL, or {@code null} — always {@code null} until image.md's builder
 *     exists (tracked gap, same as category's {@code imageUrl})
 * @param altText alt text for this use of the image, or {@code null}
 * @param position gallery order; position 0 is the main image
 */
public record ProductImageRefResponse(UUID id, String url, String altText, int position) {}
