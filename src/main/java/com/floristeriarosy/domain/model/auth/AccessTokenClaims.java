package com.floristeriarosy.domain.model.auth;

import java.util.UUID;

/**
 * The claims carried by a JWT this module issues or parses (auth.md, section 3.1). Never carries
 * PII: subject is a UUID, never an email.
 *
 * @param subjectId the admin (or, later, customer) the token belongs to
 * @param type {@code ACCESS} or {@code MFA}
 * @param subjectType {@code null} for an {@code MFA} token, which predates knowing the caller's
 *     final role
 * @param role {@code OWNER}/{@code ADMIN}, {@code null} for an {@code MFA} token
 * @param passwordChangeRequired {@code true} only on an {@code ACCESS} token for a session with a
 *     still-provisional password (auth.md, rule 3.9)
 */
public record AccessTokenClaims(
    UUID subjectId,
    TokenType type,
    SubjectType subjectType,
    String role,
    boolean passwordChangeRequired) {}
