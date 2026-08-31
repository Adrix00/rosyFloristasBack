package com.floristeriarosy.infrastructure.web.response.category;

import java.util.UUID;

public record CategorySummaryResponse(
    UUID id, String name, String slug, String imageUrl, int position) {}
