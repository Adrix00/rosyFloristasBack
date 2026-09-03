package com.floristeriarosy.application.auth.command;

/**
 * Closes the session of the device that presented this cookie (auth.md, rule 3.7).
 *
 * @param refreshToken the plaintext refresh token presented in the cookie, or {@code null} if
 *     absent
 */
public record LogoutCommand(String refreshToken) {}
