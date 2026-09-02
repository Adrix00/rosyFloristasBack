package com.floristeriarosy.application.admin.dto;

/**
 * @param temporaryPassword the newly generated provisional password, in plaintext — returned once
 *     and never persisted or logged (admin.md, section 6)
 */
public record PasswordResetResult(String temporaryPassword) {}
