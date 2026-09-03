package com.floristeriarosy.application.auth.port.out;

import com.floristeriarosy.domain.model.auth.AccessTokenClaims;
import java.time.Duration;
import java.util.Optional;

/** Issues and parses the JWTs this module hands out (auth.md, section 3.1). Never carries PII. */
public interface AccessTokenPort {

  /**
   * @param claims the claims to embed
   * @param ttl how long the token is valid for
   * @return the signed, compact JWT
   */
  String issue(AccessTokenClaims claims, Duration ttl);

  /**
   * Decodes and verifies the signature and expiry of a token this port previously issued. Does not
   * enforce {@code typ}: a caller that needs a specific token kind (e.g. only {@code MFA}) checks
   * {@link AccessTokenClaims#type()} itself.
   *
   * @param token a compact JWT, as previously returned by {@link #issue}
   * @return the decoded claims, or empty if the token is malformed, expired, or has an invalid
   *     signature
   */
  Optional<AccessTokenClaims> parse(String token);
}
