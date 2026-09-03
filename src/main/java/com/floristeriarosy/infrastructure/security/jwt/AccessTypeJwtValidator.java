package com.floristeriarosy.infrastructure.security.jwt;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Rejects any JWT whose {@code typ} claim is not {@code "access"} (auth.md, rule 3.3). Without
 * this, the ephemeral {@code mfaToken} would authenticate against the resource server exactly like
 * a real session, skipping the second factor entirely — the single most serious failure this branch
 * could ship with.
 */
public final class AccessTypeJwtValidator implements OAuth2TokenValidator<Jwt> {

  private static final OAuth2Error NOT_AN_ACCESS_TOKEN =
      new OAuth2Error("invalid_token", "The token is not an access token", null);

  /**
   * @param token the decoded JWT, already signature-verified by the caller
   * @return success only if {@code typ = "access"}
   */
  @Override
  public OAuth2TokenValidatorResult validate(Jwt token) {
    if (!AccessTokenJwtClaims.ACCESS_TYPE_VALUE.equals(
        token.getClaimAsString(AccessTokenJwtClaims.TYPE))) {
      return OAuth2TokenValidatorResult.failure(NOT_AN_ACCESS_TOKEN);
    }
    return OAuth2TokenValidatorResult.success();
  }
}
