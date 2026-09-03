package com.floristeriarosy.application.auth.service;

import com.floristeriarosy.application.admin.port.out.AdminReadPort;
import com.floristeriarosy.application.auth.command.AdminLoginCommand;
import com.floristeriarosy.application.auth.dto.AdminLoginDto;
import com.floristeriarosy.application.auth.port.in.AdminLoginUseCase;
import com.floristeriarosy.application.auth.port.out.AccessTokenPort;
import com.floristeriarosy.application.auth.port.out.PasswordHasherPort;
import com.floristeriarosy.application.shared.dto.AuditAction;
import com.floristeriarosy.application.shared.port.out.AuditLogPort;
import com.floristeriarosy.application.shared.port.out.PiiCryptoPort;
import com.floristeriarosy.domain.exception.auth.InvalidCredentialsException;
import com.floristeriarosy.domain.model.admin.Admin;
import com.floristeriarosy.domain.model.auth.AccessTokenClaims;
import com.floristeriarosy.domain.model.auth.TokenType;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements {@link AdminLoginUseCase}: step 1 of the admin login (auth.md, rule 3.3). Never writes
 * anything but the {@code LOGIN_FAILED} audit row.
 */
@Service
@Transactional
public class AdminLoginService implements AdminLoginUseCase {

  private static final String ENTITY_TYPE = "admin_user";

  /**
   * Not a real credential: hashed once at startup so {@link #execute} can run a full Argon2id
   * verification even when the email does not exist (00-security-validation-integrity.md, rule 7).
   * Without this, an unknown email returns faster than a known one with a wrong password, which
   * turns the endpoint into an account enumerator.
   */
  private static final String DECOY_PASSWORD = "uniform-timing-decoy-password-never-stored";

  private static final Logger LOGGER = LoggerFactory.getLogger(AdminLoginService.class);

  private final AdminReadPort adminReadPort;
  private final PiiCryptoPort piiCryptoPort;
  private final PasswordHasherPort passwordHasherPort;
  private final AccessTokenPort accessTokenPort;
  private final AuditLogPort auditLogPort;
  private final Duration mfaTokenTtl;
  private final String decoyHash;

  /**
   * @param adminReadPort looks up the admin by normalized email
   * @param piiCryptoPort computes the email HMAC for the lookup (ADR-005)
   * @param passwordHasherPort verifies the password, real or decoy (ADR-005)
   * @param accessTokenPort issues the ephemeral {@code mfaToken}
   * @param auditLogPort records a failed attempt (ADR-010)
   * @param mfaTokenTtl {@code app.security.jwt.mfa-token-ttl}
   */
  public AdminLoginService(
      AdminReadPort adminReadPort,
      PiiCryptoPort piiCryptoPort,
      PasswordHasherPort passwordHasherPort,
      AccessTokenPort accessTokenPort,
      AuditLogPort auditLogPort,
      @Value("${app.security.jwt.mfa-token-ttl}") Duration mfaTokenTtl) {
    this.adminReadPort = adminReadPort;
    this.piiCryptoPort = piiCryptoPort;
    this.passwordHasherPort = passwordHasherPort;
    this.accessTokenPort = accessTokenPort;
    this.auditLogPort = auditLogPort;
    this.mfaTokenTtl = mfaTokenTtl;
    this.decoyHash = passwordHasherPort.hash(DECOY_PASSWORD);
  }

  /**
   * Verifies email and password and, on success, issues a {@code typ = "mfa"} token (auth.md, rule
   * 3.3). Never logs the email or the password.
   *
   * @param command email and password
   * @return the ephemeral {@code mfaToken} and whether TOTP enrollment is still pending
   * @throws InvalidCredentialsException email unknown, password wrong, or admin inactive
   */
  @Override
  public AdminLoginDto execute(AdminLoginCommand command) {
    LOGGER.debug("adminLogin");

    String normalizedEmail = normalize(command.email());
    byte[] emailHash = piiCryptoPort.hmac(normalizedEmail);
    Optional<Admin> adminOpt = adminReadPort.findByEmailHash(emailHash);

    boolean passwordMatches;
    if (adminOpt.isPresent()) {
      passwordMatches =
          passwordHasherPort.matches(command.password(), adminOpt.get().passwordHash());
    } else {
      // Uniform timing (00-security, rule 7): run the same Argon2id cost even though there is no
      // real hash to compare against.
      passwordHasherPort.matches(command.password(), decoyHash);
      passwordMatches = false;
    }

    boolean success = adminOpt.isPresent() && adminOpt.get().active() && passwordMatches;
    if (!success) {
      UUID targetId = adminOpt.map(admin -> admin.id().value()).orElse(null);
      auditLogPort.record(targetId, AuditAction.LOGIN_FAILED, ENTITY_TYPE, targetId, List.of());
      LOGGER.debug("adminLogin -> 401 INVALID_CREDENTIALS");
      throw new InvalidCredentialsException("Invalid email or password");
    }

    Admin admin = adminOpt.get();
    String mfaToken =
        accessTokenPort.issue(
            new AccessTokenClaims(admin.id().value(), TokenType.MFA, null, null, false),
            mfaTokenTtl);
    AdminLoginDto result =
        new AdminLoginDto(mfaToken, mfaTokenTtl.toSeconds(), !admin.totpEnabled());
    LOGGER.debug("adminLogin -> enrollmentRequired={}", result.enrollmentRequired());
    return result;
  }

  /**
   * @param email the raw email from the request
   * @return {@code email}, trimmed and lower-cased (00-security-validation-integrity.md, section 4:
   *     normalize before hashing so equivalent inputs share one hash)
   */
  private String normalize(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }
}
