package com.floristeriarosy.application.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.admin.port.out.AdminReadPort;
import com.floristeriarosy.application.admin.port.out.AdminWritePort;
import com.floristeriarosy.application.auth.command.VerifyAdminMfaCommand;
import com.floristeriarosy.application.auth.dto.AuthDto;
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
import com.floristeriarosy.domain.model.admin.AdminRole;
import com.floristeriarosy.domain.model.admin.valueobject.AdminId;
import com.floristeriarosy.domain.model.auth.AccessTokenClaims;
import com.floristeriarosy.domain.model.auth.RefreshToken;
import com.floristeriarosy.domain.model.auth.SubjectType;
import com.floristeriarosy.domain.model.auth.TokenType;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link VerifyAdminMfaService}: step 2 of the admin login (auth.md, rule 3.3). */
@ExtendWith(MockitoExtension.class)
class VerifyAdminMfaServiceTest {

  @Mock private AccessTokenPort accessTokenPort;
  @Mock private AdminReadPort adminReadPort;
  @Mock private AdminWritePort adminWritePort;
  @Mock private PiiCryptoPort piiCryptoPort;
  @Mock private TotpPort totpPort;
  @Mock private RefreshTokenWritePort refreshTokenWritePort;
  @Mock private AuditLogPort auditLogPort;

  private VerifyAdminMfaService service;

  private Admin enrolledAdmin() {
    Admin admin =
        Admin.create(
            AdminId.newId(),
            "encrypted".getBytes(StandardCharsets.UTF_8),
            "hash".getBytes(StandardCharsets.UTF_8),
            "argon2-hash",
            AdminRole.OWNER);
    admin.enrollTotp("secret".getBytes(StandardCharsets.UTF_8));
    return admin;
  }

  private void newService() {
    service =
        new VerifyAdminMfaService(
            accessTokenPort,
            adminReadPort,
            adminWritePort,
            piiCryptoPort,
            totpPort,
            refreshTokenWritePort,
            auditLogPort,
            Duration.ofMinutes(5),
            Duration.ofHours(12));
  }

  @Test
  void confirmsTotpStartsAFamilyAndIssuesTheAccessToken() {
    newService();
    Admin admin = enrolledAdmin();
    AccessTokenClaims mfaClaims =
        new AccessTokenClaims(admin.id().value(), TokenType.MFA, null, null, false);
    when(accessTokenPort.parse("mfa-token")).thenReturn(Optional.of(mfaClaims));
    when(adminReadPort.findById(admin.id())).thenReturn(Optional.of(admin));
    when(piiCryptoPort.decrypt(admin.totpSecretEncrypted())).thenReturn("SECRET");
    when(totpPort.verify("SECRET", "123456", null)).thenReturn(Optional.of(99L));
    when(accessTokenPort.issue(any(AccessTokenClaims.class), eq(Duration.ofMinutes(5))))
        .thenReturn("access-token");

    AuthDto dto = service.execute(new VerifyAdminMfaCommand("mfa-token", "123456"));

    assertThat(dto.accessToken()).isEqualTo("access-token");
    assertThat(dto.refreshToken()).isNotBlank();
    assertThat(dto.subjectType()).isEqualTo(SubjectType.ADMIN);
    assertThat(dto.role()).isEqualTo("OWNER");
    assertThat(admin.totpEnabled()).isTrue();
    verify(adminWritePort).save(admin);
    verify(refreshTokenWritePort).save(any(RefreshToken.class));
    verify(auditLogPort)
        .record(admin.id().value(), AuditAction.LOGIN, "admin_user", admin.id().value(), List.of());
  }

  @Test
  void rejectsAMissingOrUnparseableMfaToken() {
    newService();
    when(accessTokenPort.parse("garbage")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.execute(new VerifyAdminMfaCommand("garbage", "123456")))
        .isInstanceOf(InvalidMfaTokenException.class);
    verify(refreshTokenWritePort, never()).save(any());
  }

  @Test
  void rejectsWhenTotpHasNeverBeenEnrolled() {
    newService();
    Admin admin =
        Admin.create(
            AdminId.newId(),
            "encrypted".getBytes(StandardCharsets.UTF_8),
            "hash".getBytes(StandardCharsets.UTF_8),
            "argon2-hash",
            AdminRole.ADMIN);
    AccessTokenClaims mfaClaims =
        new AccessTokenClaims(admin.id().value(), TokenType.MFA, null, null, false);
    when(accessTokenPort.parse("mfa-token")).thenReturn(Optional.of(mfaClaims));
    when(adminReadPort.findById(admin.id())).thenReturn(Optional.of(admin));

    assertThatThrownBy(() -> service.execute(new VerifyAdminMfaCommand("mfa-token", "123456")))
        .isInstanceOf(TotpEnrollmentRequiredException.class);
  }

  @Test
  void rejectsAWrongOrAlreadyUsedCodeAndLogsAFailure() {
    newService();
    Admin admin = enrolledAdmin();
    AccessTokenClaims mfaClaims =
        new AccessTokenClaims(admin.id().value(), TokenType.MFA, null, null, false);
    when(accessTokenPort.parse("mfa-token")).thenReturn(Optional.of(mfaClaims));
    when(adminReadPort.findById(admin.id())).thenReturn(Optional.of(admin));
    when(piiCryptoPort.decrypt(admin.totpSecretEncrypted())).thenReturn("SECRET");
    when(totpPort.verify("SECRET", "000000", null)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.execute(new VerifyAdminMfaCommand("mfa-token", "000000")))
        .isInstanceOf(InvalidTotpCodeException.class);

    verify(auditLogPort)
        .record(
            admin.id().value(), AuditAction.LOGIN_FAILED, "admin_user", admin.id().value(), List.of());
    verify(adminWritePort, never()).save(any());
    verify(refreshTokenWritePort, never()).save(any());
  }
}
