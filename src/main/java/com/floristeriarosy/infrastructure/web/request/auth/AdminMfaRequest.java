package com.floristeriarosy.infrastructure.web.request.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * @param mfaToken required; the ephemeral token returned by {@code POST /auth/admin/login}
 * @param code required; exactly 6 digits
 */
public record AdminMfaRequest(
    @NotBlank String mfaToken, @NotBlank @Pattern(regexp = "\\d{6}") String code) {}
