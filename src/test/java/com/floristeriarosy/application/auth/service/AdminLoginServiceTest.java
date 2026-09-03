package com.floristeriarosy.application.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.admin.port.out.AdminReadPort;
import com.floristeriarosy.application.auth.command.AdminLoginCommand;
import com.floristeriarosy.application.auth.dto.AdminLoginDto;
import com.floristeriarosy.application.auth.port.out.AccessTokenPort;
import com.floristeriarosy.application.auth.port.out.PasswordHasherPort;
import com.floristeriarosy.application.shared.dto.AuditAction;
import com.floristeriarosy.application.shared.port.out.AuditLogPort;
import com.floristeriarosy.application.shared.port.out.PiiCryptoPort;
import com.floristeriarosy.domain.exception.auth.InvalidCredentialsException;
import com.floristeriarosy.domain.model.admin.Admin;
import com.floristeriarosy.domain.model.admin.AdminRole;
import com.floristeriarosy.domain.model.admin.valueobject.AdminId;
import com.floristeriarosy.domain.model.auth.AccessTokenClaims;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link AdminLoginService}: step 1 of the admin login (auth.md, rule 3.3). */
@ExtendWith(MockitoExtension.class)
class AdminLoginServiceTest {

  @Mock private AdminReadPort adminReadPort;
  @Mock private PiiCryptoPort piiCryptoPort;
  @Mock private PasswordHasherPort passwordHasherPort;
  @Mock private AccessTokenPort accessTokenPort;
  @Mock private AuditLogPort auditLogPort;

  private AdminLoginService service;

  @BeforeEach
  void setUp() {
    when(passwordHasherPort.hash(any())).thenReturn("decoy-hash");
    service =
        new AdminLoginService(
            adminReadPort,
            piiCryptoPort,
            passwordHasherPort,
            accessTokenPort,
            auditLogPort,
            Duration.ofMinutes(5));
  }

  private Admin admin(boolean active, boolean totpEnabled) {
    Admin created =
        Admin.create(
            AdminId.newId(),
            "encrypted".getBytes(StandardCharsets.UTF_8),
            "hash".getBytes(StandardCharsets.UTF_8),
            "argon2-hash",
            AdminRole.OWNER);
    if (!active) {
      created.deactivate();
    }
    if (totpEnabled) {
      created.enrollTotp("secret".getBytes(StandardCharsets.UTF_8));
      created.confirmTotp(1L);
    }
    return created;
  }

  @Test
  void issuesAnMfaTokenAndFlagsEnrollmentRequiredWhenTotpIsNotYetEnrolled() {
    Admin admin = admin(true, false);
    when(piiCryptoPort.hmac("owner@rosy.test")).thenReturn("hash".getBytes(StandardCharsets.UTF_8));
    when(adminReadPort.findByEmailHash(any())).thenReturn(Optional.of(admin));
    when(passwordHasherPort.matches("Correct!234", "argon2-hash")).thenReturn(true);
    when(accessTokenPort.issue(any(AccessTokenClaims.class), eq(Duration.ofMinutes(5))))
        .thenReturn("mfa-token");

    AdminLoginDto dto =
        service.execute(new AdminLoginCommand(" Owner@Rosy.test ", "Correct!234"));

    assertThat(dto.mfaToken()).isEqualTo("mfa-token");
    assertThat(dto.enrollmentRequired()).isTrue();
    assertThat(dto.expiresInSeconds()).isEqualTo(300);
    verify(auditLogPort, never()).record(any(), any(), any(), any(), any());
  }

  @Test
  void doesNotFlagEnrollmentRequiredWhenTotpIsAlreadyEnrolled() {
    Admin admin = admin(true, true);
    when(piiCryptoPort.hmac("owner@rosy.test")).thenReturn("hash".getBytes(StandardCharsets.UTF_8));
    when(adminReadPort.findByEmailHash(any())).thenReturn(Optional.of(admin));
    when(passwordHasherPort.matches("Correct!234", "argon2-hash")).thenReturn(true);
    when(accessTokenPort.issue(any(AccessTokenClaims.class), any())).thenReturn("mfa-token");

    AdminLoginDto dto = service.execute(new AdminLoginCommand("owner@rosy.test", "Correct!234"));

    assertThat(dto.enrollmentRequired()).isFalse();
  }

  @Test
  void rejectsAnUnknownEmailWithUniformTimingAndLogsAFailureWithNoActor() {
    when(piiCryptoPort.hmac("unknown@rosy.test"))
        .thenReturn("hash".getBytes(StandardCharsets.UTF_8));
    when(adminReadPort.findByEmailHash(any())).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> service.execute(new AdminLoginCommand("unknown@rosy.test", "whatever")))
        .isInstanceOf(InvalidCredentialsException.class);

    // Uniform timing (00-security, rule 7): the decoy hash is checked even though no admin exists.
    verify(passwordHasherPort).matches("whatever", "decoy-hash");
    verify(auditLogPort)
        .record(isNull(), eq(AuditAction.LOGIN_FAILED), eq("admin_user"), isNull(), eq(List.of()));
  }

  @Test
  void rejectsAWrongPasswordAndLogsAFailureWithTheAdminAsActor() {
    Admin admin = admin(true, false);
    when(piiCryptoPort.hmac("owner@rosy.test")).thenReturn("hash".getBytes(StandardCharsets.UTF_8));
    when(adminReadPort.findByEmailHash(any())).thenReturn(Optional.of(admin));
    when(passwordHasherPort.matches("Wrong!234", "argon2-hash")).thenReturn(false);

    assertThatThrownBy(() -> service.execute(new AdminLoginCommand("owner@rosy.test", "Wrong!234")))
        .isInstanceOf(InvalidCredentialsException.class);

    verify(auditLogPort)
        .record(
            eq(admin.id().value()),
            eq(AuditAction.LOGIN_FAILED),
            eq("admin_user"),
            eq(admin.id().value()),
            eq(List.of()));
  }

  @Test
  void rejectsAnInactiveAdminEvenWithTheCorrectPassword() {
    Admin admin = admin(false, false);
    when(piiCryptoPort.hmac("owner@rosy.test")).thenReturn("hash".getBytes(StandardCharsets.UTF_8));
    when(adminReadPort.findByEmailHash(any())).thenReturn(Optional.of(admin));
    when(passwordHasherPort.matches("Correct!234", "argon2-hash")).thenReturn(true);

    assertThatThrownBy(
            () -> service.execute(new AdminLoginCommand("owner@rosy.test", "Correct!234")))
        .isInstanceOf(InvalidCredentialsException.class);
  }
}
