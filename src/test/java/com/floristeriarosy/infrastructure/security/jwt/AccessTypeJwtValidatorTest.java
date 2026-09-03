package com.floristeriarosy.infrastructure.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

class AccessTypeJwtValidatorTest {

  private final AccessTypeJwtValidator validator = new AccessTypeJwtValidator();

  @Test
  void succeedsForATypAccessToken() {
    Jwt jwt = jwtWithType("access");

    OAuth2TokenValidatorResult result = validator.validate(jwt);

    assertThat(result.hasErrors()).isFalse();
  }

  @Test
  void failsForATypMfaToken() {
    Jwt jwt = jwtWithType("mfa");

    OAuth2TokenValidatorResult result = validator.validate(jwt);

    assertThat(result.hasErrors()).isTrue();
  }

  @Test
  void failsWhenTheTypClaimIsMissing() {
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .claim("sub", "00000000-0000-0000-0000-000000000000")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();

    OAuth2TokenValidatorResult result = validator.validate(jwt);

    assertThat(result.hasErrors()).isTrue();
  }

  private Jwt jwtWithType(String type) {
    return Jwt.withTokenValue("token")
        .header("alg", "HS256")
        .claim("sub", "00000000-0000-0000-0000-000000000000")
        .claim("typ", type)
        .claims(claims -> claims.putAll(Map.of()))
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(60))
        .build();
  }
}
