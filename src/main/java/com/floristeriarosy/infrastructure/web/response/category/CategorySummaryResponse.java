package com.floristeriarosy.infrastructure.web.response.category;

import java.util.UUID;

/**
 * Category shape for listings — no description or timestamps.
 *
 * @param id the identifier
 * @param name the category name
 * @param slug the generated slug
 * @param imageUrl the public CDN URL, or {@code null} (see {@link CategoryResponse#imageUrl})
 * @param position position in the public catalog
 */
public record CategorySummaryResponse(
    UUID id, String name, String slug, String imageUrl, int position) {}
