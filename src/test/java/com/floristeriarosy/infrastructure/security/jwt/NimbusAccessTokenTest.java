package com.floristeriarosy.infrastructure.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import com.floristeriarosy.domain.model.auth.AccessTokenClaims;
import com.floristeriarosy.domain.model.auth.SubjectType;
import com.floristeriarosy.domain.model.auth.TokenType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NimbusAccessTokenTest {

  private static final String SECRET = "test-only-secret-at-least-32-bytes-long-for-hs256";

  private final NimbusAccessToken accessToken = new NimbusAccessToken(SECRET);

  @Test
  void issueThenParseRoundTripsEveryClaim() {
    UUID subjectId = UUID.randomUUID();
    AccessTokenClaims claims =
        new AccessTokenClaims(subjectId, TokenType.ACCESS, SubjectType.ADMIN, "OWNER", true);

    String token = accessToken.issue(claims, Duration.ofMinutes(5));
    Optional<AccessTokenClaims> parsed = accessToken.parse(token);

    assertThat(parsed).contains(claims);
  }

  @Test
  void issueOmitsSubjectTypeAndRoleWhenAbsentFromAnMfaToken() {
    UUID subjectId = UUID.randomUUID();
    AccessTokenClaims mfaClaims = new AccessTokenClaims(subjectId, TokenType.MFA, null, null, false);

    String token = accessToken.issue(mfaClaims, Duration.ofMinutes(5));
    Optional<AccessTokenClaims> parsed = accessToken.parse(token);

    assertThat(parsed).contains(mfaClaims);
  }

  @Test
  void parseRejectsAnExpiredToken() {
    // A negative TTL would build an invalid Jwt (expiresAt before issuedAt); instead, issue at a
    // fixed instant with a short TTL, then decode as of a later fixed instant well past both the
    // TTL and the validator's clock-skew tolerance — deterministic, no real sleep needed.
    Instant issuedAt = Instant.parse("2026-01-01T00:00:00Z");
    Instant wellAfterExpiry = issuedAt.plus(Duration.ofMinutes(10));
    NimbusAccessToken issuer = new NimbusAccessToken(SECRET, Clock.fixed(issuedAt, ZoneOffset.UTC));
    NimbusAccessToken laterVerifier =
        new NimbusAccessToken(SECRET, Clock.fixed(wellAfterExpiry, ZoneOffset.UTC));
    AccessTokenClaims claims =
        new AccessTokenClaims(UUID.randomUUID(), TokenType.ACCESS, SubjectType.ADMIN, "ADMIN", false);
    String token = issuer.issue(claims, Duration.ofMinutes(5));

    Optional<AccessTokenClaims> parsed = laterVerifier.parse(token);

    assertThat(parsed).isEmpty();
  }

  @Test
  void parseRejectsATokenSignedWithADifferentSecret() {
    NimbusAccessToken otherSigner = new NimbusAccessToken("a-completely-different-secret-value-32b");
    AccessTokenClaims claims =
        new AccessTokenClaims(UUID.randomUUID(), TokenType.ACCESS, SubjectType.ADMIN, "ADMIN", false);
    String token = otherSigner.issue(claims, Duration.ofMinutes(5));

    Optional<AccessTokenClaims> parsed = accessToken.parse(token);

    assertThat(parsed).isEmpty();
  }

  @Test
  void parseRejectsAMalformedToken() {
    Optional<AccessTokenClaims> parsed = accessToken.parse("not-a-jwt-at-all");

    assertThat(parsed).isEmpty();
  }
}
