package com.floristeriarosy.application.auth.service;

import com.floristeriarosy.application.admin.port.out.AdminReadPort;
import com.floristeriarosy.application.admin.port.out.AdminWritePort;
import com.floristeriarosy.application.auth.command.EnrollAdminTotpCommand;
import com.floristeriarosy.application.auth.dto.TotpEnrollmentDto;
import com.floristeriarosy.application.auth.port.in.EnrollAdminTotpUseCase;
import com.floristeriarosy.application.auth.port.out.AccessTokenPort;
import com.floristeriarosy.application.auth.port.out.TotpPort;
import com.floristeriarosy.application.shared.port.out.PiiCryptoPort;
import com.floristeriarosy.domain.exception.auth.InvalidMfaTokenException;
import com.floristeriarosy.domain.exception.auth.TotpAlreadyEnrolledException;
import com.floristeriarosy.domain.model.admin.Admin;
import com.floristeriarosy.domain.model.admin.valueobject.AdminId;
import com.floristeriarosy.domain.model.auth.AccessTokenClaims;
import com.floristeriarosy.domain.model.auth.TokenType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements {@link EnrollAdminTotpUseCase}: generates a new TOTP secret for the admin identified
 * by the {@code mfaToken} (auth.md, rule 3.4).
 */
@Service
@Transactional
public class EnrollAdminTotpService implements EnrollAdminTotpUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(EnrollAdminTotpService.class);

  private final AccessTokenPort accessTokenPort;
  private final AdminReadPort adminReadPort;
  private final AdminWritePort adminWritePort;
  private final PiiCryptoPort piiCryptoPort;
  private final TotpPort totpPort;

  /**
   * @param accessTokenPort validates and decodes the {@code mfaToken}
   * @param adminReadPort loads the admin the {@code mfaToken} identifies
   * @param adminWritePort persists the newly generated secret
   * @param piiCryptoPort encrypts the secret and decrypts the email for the enrollment URI
   *     (ADR-005)
   * @param totpPort generates the secret and builds the {@code otpauth://} URI
   */
  public EnrollAdminTotpService(
      AccessTokenPort accessTokenPort,
      AdminReadPort adminReadPort,
      AdminWritePort adminWritePort,
      PiiCryptoPort piiCryptoPort,
      TotpPort totpPort) {
    this.accessTokenPort = accessTokenPort;
    this.adminReadPort = adminReadPort;
    this.adminWritePort = adminWritePort;
    this.piiCryptoPort = piiCryptoPort;
    this.totpPort = totpPort;
  }

  /**
   * Stores the secret with {@code totp_enabled} still {@code false} (auth.md, rule 3.4): it only
   * becomes active once {@code VerifyAdminMfaUseCase} confirms a valid code.
   *
   * @param command the {@code mfaToken}
   * @return the enrollment URI and secret, returned only this once
   * @throws InvalidMfaTokenException the token is missing, expired, or not a {@code mfa} token
   * @throws TotpAlreadyEnrolledException the admin already has TOTP enrolled
   */
  @Override
  public TotpEnrollmentDto execute(EnrollAdminTotpCommand command) {
    LOGGER.debug("enrollAdminTotp");

    Admin admin = resolveAdmin(command.mfaToken());
    if (admin.totpEnabled()) {
      LOGGER.debug("enrollAdminTotp adminId={} -> 409 TOTP_ALREADY_ENROLLED", admin.id());
      throw new TotpAlreadyEnrolledException("TOTP is already enrolled for this admin");
    }

    String secret = totpPort.generateSecret();
    admin.enrollTotp(piiCryptoPort.encrypt(secret));
    adminWritePort.save(admin);

    String email = piiCryptoPort.decrypt(admin.emailEncrypted());
    TotpEnrollmentDto result = new TotpEnrollmentDto(totpPort.otpauthUri(secret, email), secret);
    LOGGER.debug("enrollAdminTotp adminId={} -> enrolled", admin.id());
    return result;
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
