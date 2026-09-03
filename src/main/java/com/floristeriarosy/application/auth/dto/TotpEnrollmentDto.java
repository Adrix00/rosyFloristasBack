package com.floristeriarosy.application.auth.dto;

/**
 * Result of generating a TOTP secret (auth.md, rule 3.4). Returned once — a second enrollment call
 * overwrites the secret and there is no way to read the first one again. Never logged as a whole —
 * {@code secret} is a secret (ADR-005).
 *
 * @param otpauthUri the {@code otpauth://totp/...} URI, for the enrollment QR code
 * @param secret the Base32-encoded secret, for manual entry if scanning the QR fails
 */
public record TotpEnrollmentDto(String otpauthUri, String secret) {}
