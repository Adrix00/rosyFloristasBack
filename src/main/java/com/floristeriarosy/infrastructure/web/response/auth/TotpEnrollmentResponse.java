package com.floristeriarosy.infrastructure.web.response.auth;

/**
 * Returned once — a second enrollment call overwrites the secret and there is no way to read the
 * first one again (auth.md, rule 3.4).
 *
 * @param otpauthUri the {@code otpauth://totp/...} URI, for the enrollment QR code
 * @param secret the Base32-encoded secret, for manual entry if scanning the QR fails
 */
public record TotpEnrollmentResponse(String otpauthUri, String secret) {}
