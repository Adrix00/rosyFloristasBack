package com.floristeriarosy.application.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.admin.port.out.AdminReadPort;
import com.floristeriarosy.application.admin.port.out.AdminWritePort;
import com.floristeriarosy.application.auth.command.EnrollAdminTotpCommand;
import com.floristeriarosy.application.auth.dto.TotpEnrollmentDto;
import com.floristeriarosy.application.auth.port.out.AccessTokenPort;
import com.floristeriarosy.application.auth.port.out.TotpPort;
import com.floristeriarosy.application.shared.port.out.PiiCryptoPort;
import com.floristeriarosy.domain.exception.auth.InvalidMfaTokenException;
import com.floristeriarosy.domain.exception.auth.TotpAlreadyEnrolledException;
import com.floristeriarosy.domain.model.admin.Admin;
import com.floristeriarosy.domain.model.admin.AdminRole;
import com.floristeriarosy.domain.model.admin.valueobject.AdminId;
import com.floristeriarosy.domain.model.auth.AccessTokenClaims;
import com.floristeriarosy.domain.model.auth.SubjectType;
import com.floristeriarosy.domain.model.auth.TokenType;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link EnrollAdminTotpService}: generates a TOTP secret for the {@code mfaToken}'s admin. */
@ExtendWith(MockitoExtension.class)
class EnrollAdminTotpServiceTest {

  @Mock private AccessTokenPort accessTokenPort;
  @Mock private AdminReadPort adminReadPort;
  @Mock private AdminWritePort adminWritePort;
  @Mock private PiiCryptoPort piiCryptoPort;
  @Mock private TotpPort totpPort;

  private EnrollAdminTotpService service;

  private Admin freshAdmin() {
    return Admin.create(
        AdminId.newId(),
        "encrypted".getBytes(StandardCharsets.UTF_8),
        "hash".getBytes(StandardCharsets.UTF_8),
        "argon2-hash",
        AdminRole.ADMIN);
  }

  @Test
  void generatesAndStoresASecretWithTotpStillDisabled() {
    service = new EnrollAdminTotpService(accessTokenPort, adminReadPort, adminWritePort, piiCryptoPort, totpPort);
    Admin admin = freshAdmin();
    AccessTokenClaims mfaClaims =
        new AccessTokenClaims(admin.id().value(), TokenType.MFA, null, null, false);
    when(accessTokenPort.parse("mfa-token")).thenReturn(Optional.of(mfaClaims));
    when(adminReadPort.findById(admin.id())).thenReturn(Optional.of(admin));
    when(totpPort.generateSecret()).thenReturn("SECRETBASE32");
    when(piiCryptoPort.encrypt("SECRETBASE32")).thenReturn("cipher".getBytes(StandardCharsets.UTF_8));
    when(piiCryptoPort.decrypt(admin.emailEncrypted())).thenReturn("admin@rosy.test");
    when(totpPort.otpauthUri("SECRETBASE32", "admin@rosy.test")).thenReturn("otpauth://totp/x");

    TotpEnrollmentDto dto = service.execute(new EnrollAdminTotpCommand("mfa-token"));

    assertThat(dto.secret()).isEqualTo("SECRETBASE32");
    assertThat(dto.otpauthUri()).isEqualTo("otpauth://totp/x");
    assertThat(admin.totpEnabled()).isFalse();
    verify(adminWritePort).save(admin);
  }

  @Test
  void rejectsAMissingOrUnparseableMfaToken() {
    service = new EnrollAdminTotpService(accessTokenPort, adminReadPort, adminWritePort, piiCryptoPort, totpPort);
    when(accessTokenPort.parse("garbage")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.execute(new EnrollAdminTotpCommand("garbage")))
        .isInstanceOf(InvalidMfaTokenException.class);
    verify(adminWritePort, never()).save(any());
  }

  @Test
  void rejectsATokenThatIsNotAnMfaToken() {
    service = new EnrollAdminTotpService(accessTokenPort, adminReadPort, adminWritePort, piiCryptoPort, totpPort);
    AccessTokenClaims accessClaims =
        new AccessTokenClaims(UUID.randomUUID(), TokenType.ACCESS, SubjectType.ADMIN, "ADMIN", false);
    when(accessTokenPort.parse("real-access-token")).thenReturn(Optional.of(accessClaims));

    assertThatThrownBy(() -> service.execute(new EnrollAdminTotpCommand("real-access-token")))
        .isInstanceOf(InvalidMfaTokenException.class);
  }

  @Test
  void rejectsAnMfaTokenForAnAdminThatNoLongerExists() {
    service = new EnrollAdminTotpService(accessTokenPort, adminReadPort, adminWritePort, piiCryptoPort, totpPort);
    UUID missingAdminId = UUID.randomUUID();
    AccessTokenClaims mfaClaims = new AccessTokenClaims(missingAdminId, TokenType.MFA, null, null, false);
    when(accessTokenPort.parse("mfa-token")).thenReturn(Optional.of(mfaClaims));
    when(adminReadPort.findById(AdminId.of(missingAdminId))).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.execute(new EnrollAdminTotpCommand("mfa-token")))
        .isInstanceOf(InvalidMfaTokenException.class);
  }

  @Test
  void rejectsEnrollmentWhenTotpIsAlreadyEnabled() {
    service = new EnrollAdminTotpService(accessTokenPort, adminReadPort, adminWritePort, piiCryptoPort, totpPort);
    Admin admin = freshAdmin();
    admin.enrollTotp("secret".getBytes(StandardCharsets.UTF_8));
    admin.confirmTotp(1L);
    AccessTokenClaims mfaClaims =
        new AccessTokenClaims(admin.id().value(), TokenType.MFA, null, null, false);
    when(accessTokenPort.parse("mfa-token")).thenReturn(Optional.of(mfaClaims));
    when(adminReadPort.findById(admin.id())).thenReturn(Optional.of(admin));

    assertThatThrownBy(() -> service.execute(new EnrollAdminTotpCommand("mfa-token")))
        .isInstanceOf(TotpAlreadyEnrolledException.class);
    verify(adminWritePort, never()).save(any());
  }
}
