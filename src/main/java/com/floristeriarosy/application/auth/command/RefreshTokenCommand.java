package com.floristeriarosy.application.auth.command;

/**
 * Renews a session (auth.md, rule 3.5).
 *
 * @param refreshToken the plaintext refresh token presented in the cookie
 */
public record RefreshTokenCommand(String refreshToken) {}
