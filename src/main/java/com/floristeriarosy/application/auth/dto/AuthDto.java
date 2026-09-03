package com.floristeriarosy.application.auth.dto;

import com.floristeriarosy.domain.model.auth.SubjectType;
import java.time.Instant;

/**
 * Result of issuing a full session: an access token for the response body and a refresh token for
 * the controller to place in a cookie. Never logged as a whole — {@code accessToken} and {@code
 * refreshToken} are secrets (ADR-005).
 *
 * @param accessToken the signed JWT
 * @param expiresInSeconds the access token's lifetime, in seconds
 * @param subjectType {@code CUSTOMER} or {@code ADMIN}
 * @param role {@code OWNER}/{@code ADMIN}, {@code null} for a customer
 * @param passwordChangeRequired whether the session is limited to changing a provisional password
 * @param refreshToken the plaintext refresh token; the controller writes it to a cookie and never
 *     includes it in a response body
 * @param refreshTokenExpiresAt when the refresh token's family absolutely expires, used as the
 *     cookie's {@code Max-Age}
 */
public record AuthDto(
    String accessToken,
    long expiresInSeconds,
    SubjectType subjectType,
    String role,
    boolean passwordChangeRequired,
    String refreshToken,
    Instant refreshTokenExpiresAt) {}
