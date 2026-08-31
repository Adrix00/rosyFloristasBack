package com.floristeriarosy.application.category.dto;

import java.util.UUID;

/** Minimal product reference for the impact preview (category.md, section 6). */
public record CategoryProductRef(UUID id, String name, String status) {}
