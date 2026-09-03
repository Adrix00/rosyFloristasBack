package com.floristeriarosy.application.auth.service;

import com.floristeriarosy.application.admin.port.out.AdminReadPort;
import com.floristeriarosy.application.auth.command.RefreshTokenCommand;
import com.floristeriarosy.application.auth.dto.AuthDto;
import com.floristeriarosy.application.auth.port.in.RefreshTokenUseCase;
import com.floristeriarosy.application.auth.port.out.AccessTokenPort;
import com.floristeriarosy.application.auth.port.out.RefreshTokenReadPort;
import com.floristeriarosy.application.auth.port.out.RefreshTokenWritePort;
import com.floristeriarosy.application.auth.port.out.RevokeTokenFamilyPort;
import com.floristeriarosy.domain.exception.admin.AdminNotFoundException;
import com.floristeriarosy.domain.exception.auth.InvalidRefreshTokenException;
import com.floristeriarosy.domain.exception.auth.SessionRevokedException;
import com.floristeriarosy.domain.exception.auth.TokenExpiredException;
import com.floristeriarosy.domain.model.admin.Admin;
import com.floristeriarosy.domain.model.admin.valueobject.AdminId;
import com.floristeriarosy.domain.model.auth.AccessTokenClaims;
import com.floristeriarosy.domain.model.auth.RefreshToken;
import com.floristeriarosy.domain.model.auth.TokenType;
import com.floristeriarosy.domain.model.auth.valueobject.RefreshTokenId;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements {@link RefreshTokenUseCase}: single-use rotation and reuse detection (auth.md, rules
 * 3.5 and 3.6; ADR-008). The only place in the codebase allowed to write a rotated row — the {@code
 * expiresAt} copy-never-extend invariant lives here and nowhere else.
 */
@Service
@Transactional
public class RefreshTokenService implements RefreshTokenUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(RefreshTokenService.class);

  private final RefreshTokenReadPort refreshTokenReadPort;
  private final RefreshTokenWritePort refreshTokenWritePort;
  private final RevokeTokenFamilyPort revokeTokenFamilyPort;
  private final AdminReadPort adminReadPort;
  private final AccessTokenPort accessTokenPort;
  private final Duration accessTokenTtl;

  /**
   * @param refreshTokenReadPort looks up the presented token by its hash
   * @param refreshTokenWritePort revokes the presented row and persists the rotated one
   * @param revokeTokenFamilyPort revokes the whole family when reuse is detected
   * @param adminReadPort loads the admin's current role and password-change state
   * @param accessTokenPort issues the new access token
   * @param accessTokenTtl {@code app.security.jwt.access-token-ttl}
   */
  public RefreshTokenService(
      RefreshTokenReadPort refreshTokenReadPort,
      RefreshTokenWritePort refreshTokenWritePort,
      RevokeTokenFamilyPort revokeTokenFamilyPort,
      AdminReadPort adminReadPort,
      AccessTokenPort accessTokenPort,
      @Value("${app.security.jwt.access-token-ttl}") Duration accessTokenTtl) {
    this.refreshTokenReadPort = refreshTokenReadPort;
    this.refreshTokenWritePort = refreshTokenWritePort;
    this.revokeTokenFamilyPort = revokeTokenFamilyPort;
    this.adminReadPort = adminReadPort;
    this.accessTokenPort = accessTokenPort;
    this.accessTokenTtl = accessTokenTtl;
  }

  /**
   * @param command the plaintext refresh token from the cookie
   * @return the newly rotated access and refresh tokens
   * @throws InvalidRefreshTokenException no row matches the presented token
   * @throws TokenExpiredException the family's absolute expiry has passed
   * @throws SessionRevokedException the token was already revoked (reuse detected); the whole
   *     family is revoked as a side effect
   */
  @Override
  public AuthDto execute(RefreshTokenCommand command) {
    LOGGER.debug("refreshToken");

    RefreshToken presented =
        refreshTokenReadPort
            .findByHash(RefreshToken.hash(command.refreshToken()))
            .orElseThrow(() -> new InvalidRefreshTokenException("Unknown refresh token"));

    Instant now = Instant.now();
    if (presented.isExpired(now)) {
      LOGGER.debug("refreshToken familyId={} -> 401 TOKEN_EXPIRED", presented.familyId());
      throw new TokenExpiredException("Refresh token family has expired");
    }
    if (presented.isRevoked()) {
      revokeTokenFamilyPort.revokeFamily(presented.familyId());
      LOGGER.debug("refreshToken familyId={} -> 401 SESSION_REVOKED (reuse)", presented.familyId());
      throw new SessionRevokedException("Refresh token reuse detected; session revoked");
    }

    presented.revoke(now);
    refreshTokenWritePort.revoke(presented.id(), now);

    String rawNewToken = RefreshToken.generatePlaintext();
    RefreshToken rotated = presented.rotate(RefreshTokenId.newId(), RefreshToken.hash(rawNewToken));
    refreshTokenWritePort.save(rotated);

    Admin admin =
        adminReadPort
            .findById(AdminId.of(presented.subjectId()))
            .orElseThrow(
                () -> new AdminNotFoundException("Admin " + presented.subjectId() + " not found"));

    String accessToken =
        accessTokenPort.issue(
            new AccessTokenClaims(
                admin.id().value(),
                TokenType.ACCESS,
                presented.subjectType(),
                admin.role().name(),
                admin.passwordChangeRequired()),
            accessTokenTtl);

    LOGGER.debug("refreshToken familyId={} -> 200 rotated", rotated.familyId());
    return new AuthDto(
        accessToken,
        accessTokenTtl.toSeconds(),
        presented.subjectType(),
        admin.role().name(),
        admin.passwordChangeRequired(),
        rawNewToken,
        rotated.expiresAt());
  }
}
