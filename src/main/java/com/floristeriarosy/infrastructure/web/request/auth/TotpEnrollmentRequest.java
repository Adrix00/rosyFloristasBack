package com.floristeriarosy.infrastructure.web.request.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * @param mfaToken required; the ephemeral token returned by {@code POST /auth/admin/login}
 */
public record TotpEnrollmentRequest(@NotBlank String mfaToken) {}
