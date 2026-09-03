package com.floristeriarosy.application.auth.command;

/**
 * Closes every session of the subject that presented this cookie, this device included (auth.md,
 * rule 3.7).
 *
 * @param refreshToken the plaintext refresh token presented in the cookie, or {@code null} if
 *     absent
 */
public record LogoutAllCommand(String refreshToken) {}
