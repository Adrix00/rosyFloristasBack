package com.floristeriarosy.application.admin.query;

import java.util.UUID;

/**
 * @param id the admin to look up, either from a path variable ({@code GET /admin/users/{id}}) or
 *     from the authenticated caller ({@code GET /admin/me})
 */
public record GetAdminQuery(UUID id) {}
