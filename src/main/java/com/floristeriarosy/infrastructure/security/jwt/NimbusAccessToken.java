package com.floristeriarosy.infrastructure.security.jwt;

import com.floristeriarosy.application.auth.port.out.AccessTokenPort;
import com.floristeriarosy.domain.model.auth.AccessTokenClaims;
import com.floristeriarosy.domain.model.auth.SubjectType;
import com.floristeriarosy.domain.model.auth.TokenType;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

/**
 * Implements {@link AccessTokenPort} with Nimbus (HS256), the library {@code
 * spring-boot-starter-oauth2-resource-server} brings in. This adapter's own {@link #parse}
 * validates only signature and expiry — it is used to decode the ephemeral {@code mfaToken}, which
 * never reaches the resource server's {@code Authorization} header filter. The extra {@code typ =
 * "access"} check that gates real API access lives in {@code SecurityConfig}'s own decoder bean,
 * layered on the exact same secret.
 *
 * <p>One service verifies its own tokens, so HS256 is enough (a single shared secret); RS256 would
 * only earn its keep if a third party ever had to verify these tokens without holding that secret.
 */
@Component
public class NimbusAccessToken implements AccessTokenPort {

  private static final Logger LOGGER = LoggerFactory.getLogger(NimbusAccessToken.class);

  private final JwtEncoder jwtEncoder;
  private final JwtDecoder jwtDecoder;
  private final Clock clock;

  /**
   * @param secret {@code app.security.jwt.secret}, the shared HS256 signing key
   */
  @Autowired
  public NimbusAccessToken(@Value("${app.security.jwt.secret}") String secret) {
    this(secret, Clock.systemUTC());
  }

  /**
   * @param secret {@code app.security.jwt.secret}, the shared HS256 signing key
   * @param clock the time source both issuance and expiry validation use — fixed in tests so an
   *     "already expired" token can be reproduced deterministically, without a 60-second-skew-plus
   *     sleep
   */
  NimbusAccessToken(String secret, Clock clock) {
    this.clock = clock;
    SecretKeySpec key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    this.jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    JwtTimestampValidator timestampValidator = new JwtTimestampValidator();
    timestampValidator.setClock(clock);
    decoder.setJwtValidator(timestampValidator);
    this.jwtDecoder = decoder;
  }

  /**
   * @param claims the claims to embed
   * @param ttl how long the token is valid for
   * @return the signed, compact JWT
   */
  @Override
  public String issue(AccessTokenClaims claims, Duration ttl) {
    LOGGER.debug("issue subjectId={} type={}", claims.subjectId(), claims.type());
    Instant now = Instant.now(clock);
    JwtClaimsSet.Builder claimsSet =
        JwtClaimsSet.builder()
            .issuedAt(now)
            .expiresAt(now.plus(ttl))
            .subject(claims.subjectId().toString())
            .claim(AccessTokenJwtClaims.TYPE, claims.type().name().toLowerCase(Locale.ROOT));
    if (claims.subjectType() != null) {
      claimsSet.claim(AccessTokenJwtClaims.SUBJECT_TYPE, claims.subjectType().name());
    }
    if (claims.role() != null) {
      claimsSet.claim(AccessTokenJwtClaims.ROLE, claims.role());
    }
    if (claims.passwordChangeRequired()) {
      claimsSet.claim(AccessTokenJwtClaims.PASSWORD_CHANGE_REQUIRED, true);
    }
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    String token =
        jwtEncoder.encode(JwtEncoderParameters.from(header, claimsSet.build())).getTokenValue();
    LOGGER.debug("issue -> issued");
    return token;
  }

  /**
   * @param token a compact JWT, as previously returned by {@link #issue}
   * @return the decoded claims, or empty if the token is malformed, expired, or has an invalid
   *     signature
   */
  @Override
  public Optional<AccessTokenClaims> parse(String token) {
    LOGGER.debug("parse");
    try {
      Jwt jwt = jwtDecoder.decode(token);
      String subjectTypeClaim = jwt.getClaimAsString(AccessTokenJwtClaims.SUBJECT_TYPE);
      AccessTokenClaims claims =
          new AccessTokenClaims(
              UUID.fromString(jwt.getSubject()),
              TokenType.valueOf(
                  String.valueOf(jwt.getClaimAsString(AccessTokenJwtClaims.TYPE))
                      .toUpperCase(Locale.ROOT)),
              subjectTypeClaim == null ? null : SubjectType.valueOf(subjectTypeClaim),
              jwt.getClaimAsString(AccessTokenJwtClaims.ROLE),
              Boolean.TRUE.equals(jwt.getClaim(AccessTokenJwtClaims.PASSWORD_CHANGE_REQUIRED)));
      LOGGER.debug("parse -> valid type={}", claims.type());
      return Optional.of(claims);
    } catch (JwtException | IllegalArgumentException e) {
      LOGGER.debug("parse -> invalid or expired token");
      return Optional.empty();
    }
  }
}
