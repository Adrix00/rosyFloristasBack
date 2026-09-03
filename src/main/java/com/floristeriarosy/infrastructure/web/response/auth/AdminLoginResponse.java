package com.floristeriarosy.infrastructure.web.response.auth;

/**
 * @param mfaToken the ephemeral JWT to present next, to {@code POST /auth/admin/totp/enrollment} or
 *     {@code POST /auth/admin/mfa}
 * @param expiresIn the {@code mfaToken}'s lifetime, in seconds
 * @param enrollmentRequired whether the admin still needs to enroll TOTP before verifying
 */
public record AdminLoginResponse(String mfaToken, long expiresIn, boolean enrollmentRequired) {}
