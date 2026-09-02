package com.floristeriarosy.infrastructure.web.response.admin;

/**
 * @param temporaryPassword the newly generated provisional password, returned once (admin.md,
 *     section 6)
 */
public record PasswordResetResponse(String temporaryPassword) {}
