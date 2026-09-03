package com.floristeriarosy.infrastructure.web.request.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * No {@code @Size} or password-complexity annotation on {@code password}: a login endpoint checks
 * the password, it does not enforce policy on it — that would filter out old accounts and, worse,
 * reveal the policy to anyone just probing the endpoint (auth.md, section 5).
 *
 * @param email required
 * @param password required
 */
public record AdminLoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
