package com.floristeriarosy.infrastructure.web.response.auth;

/**
 * Never carries the refresh token: it travels only in the {@code HttpOnly} cookie the controller
 * sets alongside this body (auth.md, rule 3.1).
 *
 * @param accessToken the signed JWT, kept by the frontend in memory only
 * @param expiresIn the access token's lifetime, in seconds
 * @param subjectType {@code CUSTOMER} or {@code ADMIN}
 * @param role {@code OWNER}/{@code ADMIN}, {@code null} for a customer
 * @param passwordChangeRequired whether the session is limited to changing a provisional password
 */
public record AuthResponse(
    String accessToken,
    long expiresIn,
    String subjectType,
    String role,
    boolean passwordChangeRequired) {}
