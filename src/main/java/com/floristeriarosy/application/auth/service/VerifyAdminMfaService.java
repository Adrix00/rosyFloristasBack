package com.floristeriarosy.application.auth.service;

import com.floristeriarosy.application.admin.port.out.AdminReadPort;
import com.floristeriarosy.application.admin.port.out.AdminWritePort;
import com.floristeriarosy.application.auth.command.VerifyAdminMfaCommand;
import com.floristeriarosy.application.auth.dto.AuthDto;
import com.floristeriarosy.application.auth.port.in.VerifyAdminMfaUseCase;
import com.floristeriarosy.application.auth.port.out.AccessTokenPort;
import com.floristeriarosy.application.auth.port.out.RefreshTokenWritePort;
import com.floristeriarosy.application.auth.port.out.TotpPort;
import com.floristeriarosy.application.shared.dto.AuditAction;
import com.floristeriarosy.application.shared.port.out.AuditLogPort;
import com.floristeriarosy.application.shared.port.out.PiiCryptoPort;
import com.floristeriarosy.domain.exception.auth.InvalidMfaTokenException;
import com.floristeriarosy.domain.exception.auth.InvalidTotpCodeException;
import com.floristeriarosy.domain.exception.auth.TotpEnrollmentRequiredException;
import com.floristeriarosy.domain.model.admin.Admin;
import com.floristeriarosy.domain.model.admin.valueobject.AdminId;
import com.floristeriarosy.domain.model.auth.AccessTokenClaims;
import com.floristeriarosy.domain.model.auth.RefreshToken;
import com.floristeriarosy.domain.model.auth.SubjectType;
import com.floristeriarosy.domain.model.auth.TokenType;
import com.floristeriarosy.domain.model.auth.valueobject.RefreshTokenId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements {@link VerifyAdminMfaUseCase}: step 2 of the admin login (auth.md, rule 3.3). Confirms
 * the TOTP code, starts a new refresh-token family (ADR-008) and issues the access token.
 */
@Service
@Transactional
public class VerifyAdminMfaService implements VerifyAdminMfaUseCase {

  private static final String ENTITY_TYPE = "admin_user";

  private static final Logger LOGGER = LoggerFactory.getLogger(VerifyAdminMfaService.class);

  private final AccessTokenPort accessTokenPort;
  private final AdminReadPort adminReadPort;
  private final AdminWritePort adminWritePort;
  private final PiiCryptoPort piiCryptoPort;
  private final TotpPort totpPort;
  private final RefreshTokenWritePort refreshTokenWritePort;
  private final AuditLogPort auditLogPort;
  private final Duration accessTokenTtl;
  private final Duration adminRefreshTtl;

  /**
   * @param accessTokenPort validates the {@code mfaToken} and issues the final access token
   * @param adminReadPort loads the admin the {@code mfaToken} identifies
   * @param adminWritePort persists the confirmed TOTP state
   * @param piiCryptoPort decrypts the TOTP secret (ADR-005)
   * @param totpPort verifies the code
   * @param refreshTokenWritePort persists the new refresh-token family
   * @param auditLogPort records the login attempt (ADR-010)
   * @param accessTokenTtl {@code app.security.jwt.access-token-ttl}
   * @param adminRefreshTtl {@code app.security.jwt.admin-refresh-ttl}
   */
  public VerifyAdminMfaService(
      AccessTokenPort accessTokenPort,
      AdminReadPort adminReadPort,
      AdminWritePort adminWritePort,
      PiiCryptoPort piiCryptoPort,
      TotpPort totpPort,
      RefreshTokenWritePort refreshTokenWritePort,
      AuditLogPort auditLogPort,
      @Value("${app.security.jwt.access-token-ttl}") Duration accessTokenTtl,
      @Value("${app.security.jwt.admin-refresh-ttl}") Duration adminRefreshTtl) {
    this.accessTokenPort = accessTokenPort;
    this.adminReadPort = adminReadPort;
    this.adminWritePort = adminWritePort;
    this.piiCryptoPort = piiCryptoPort;
    this.totpPort = totpPort;
    this.refreshTokenWritePort = refreshTokenWritePort;
    this.auditLogPort = auditLogPort;
    this.accessTokenTtl = accessTokenTtl;
    this.adminRefreshTtl = adminRefreshTtl;
  }

  /**
   * @param command the {@code mfaToken} and the 6-digit code
   * @return the issued access and refresh tokens
   * @throws InvalidMfaTokenException the token is missing, expired, or not a {@code mfa} token
   * @throws TotpEnrollmentRequiredException the admin has not generated a TOTP secret yet
   * @throws InvalidTotpCodeException the code is wrong, out of window, or already consumed
   */
  @Override
  public AuthDto execute(VerifyAdminMfaCommand command) {
    LOGGER.debug("verifyAdminMfa");

    Admin admin = resolveAdmin(command.mfaToken());
    if (admin.totpSecretEncrypted() == null) {
      LOGGER.debug("verifyAdminMfa adminId={} -> 409 TOTP_ENROLLMENT_REQUIRED", admin.id());
      throw new TotpEnrollmentRequiredException("TOTP has not been enrolled yet");
    }

    String secret = piiCryptoPort.decrypt(admin.totpSecretEncrypted());
    Optional<Long> acceptedStep = totpPort.verify(secret, command.code(), admin.totpLastUsedStep());
    if (acceptedStep.isEmpty()) {
      auditLogPort.record(
          admin.id().value(), AuditAction.LOGIN_FAILED, ENTITY_TYPE, admin.id().value(), List.of());
      LOGGER.debug("verifyAdminMfa adminId={} -> 401 INVALID_TOTP_CODE", admin.id());
      throw new InvalidTotpCodeException("Invalid or already used TOTP code");
    }

    admin.confirmTotp(acceptedStep.get());
    adminWritePort.save(admin);

    String rawRefreshToken = RefreshToken.generatePlaintext();
    Instant refreshTokenExpiresAt = Instant.now().plus(adminRefreshTtl);
    RefreshToken refreshToken =
        RefreshToken.startFamily(
            RefreshTokenId.newId(),
            RefreshToken.hash(rawRefreshToken),
            admin.id().value(),
            SubjectType.ADMIN,
            refreshTokenExpiresAt);
    refreshTokenWritePort.save(refreshToken);

    String accessToken =
        accessTokenPort.issue(
            new AccessTokenClaims(
                admin.id().value(),
                TokenType.ACCESS,
                SubjectType.ADMIN,
                admin.role().name(),
                admin.passwordChangeRequired()),
            accessTokenTtl);

    auditLogPort.record(
        admin.id().value(), AuditAction.LOGIN, ENTITY_TYPE, admin.id().value(), List.of());
    LOGGER.debug("verifyAdminMfa adminId={} -> 200", admin.id());
    return new AuthDto(
        accessToken,
        accessTokenTtl.toSeconds(),
        SubjectType.ADMIN,
        admin.role().name(),
        admin.passwordChangeRequired(),
        rawRefreshToken,
        refreshTokenExpiresAt);
  }

  /**
   * @param mfaToken the token presented by the caller
   * @return the admin it identifies
   * @throws InvalidMfaTokenException the token is missing, expired, not a {@code mfa} token,
   *     identifies an admin that no longer exists, or that was deactivated after step 1 (auth.md,
   *     rule 3.3 — a deactivated admin must not be able to complete a login already in flight)
   */
  private Admin resolveAdmin(String mfaToken) {
    AccessTokenClaims claims =
        accessTokenPort
            .parse(mfaToken)
            .filter(candidate -> candidate.type() == TokenType.MFA)
            .orElseThrow(() -> new InvalidMfaTokenException("Invalid or expired mfaToken"));
    Admin admin =
        adminReadPort
            .findById(AdminId.of(claims.subjectId()))
            .orElseThrow(() -> new InvalidMfaTokenException("Invalid or expired mfaToken"));
    if (!admin.active()) {
      throw new InvalidMfaTokenException("Invalid or expired mfaToken");
    }
    return admin;
  }
}
