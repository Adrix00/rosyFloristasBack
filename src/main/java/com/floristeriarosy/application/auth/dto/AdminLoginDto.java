package com.floristeriarosy.application.auth.dto;

/**
 * Result of step 1 of the admin login (auth.md, rule 3.3). Never logged as a whole — {@code
 * mfaToken} is a secret (ADR-005).
 *
 * @param mfaToken the ephemeral JWT to present to {@code POST /auth/admin/totp/enrollment} or
 *     {@code POST /auth/admin/mfa}
 * @param expiresInSeconds the {@code mfaToken}'s lifetime, in seconds
 * @param enrollmentRequired whether the admin still needs to enroll TOTP before verifying
 */
public record AdminLoginDto(String mfaToken, long expiresInSeconds, boolean enrollmentRequired) {}
