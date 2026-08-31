package com.floristeriarosy.infrastructure.web.response.category;

import com.floristeriarosy.domain.model.category.CategoryStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * @param id the identifier
 * @param name the category name
 * @param slug the generated slug
 * @param description the description, or {@code null}
 * @param status {@code ACTIVE} or {@code INACTIVE}
 * @param imageUrl the public CDN URL, or {@code null} — always {@code null} until image.md's
 *     builder exists (tracked gap, dev-plan.md)
 * @param position position in the public catalog
 * @param createdAt when the row was created
 * @param updatedAt when the row was last updated
 */
public record CategoryResponse(
    UUID id,
    String name,
    String slug,
    String description,
    CategoryStatus status,
    String imageUrl,
    int position,
    Instant createdAt,
    Instant updatedAt) {}
