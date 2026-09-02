package com.floristeriarosy.application.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.admin.command.ResetAdminTotpCommand;
import com.floristeriarosy.application.admin.port.out.AdminReadPort;
import com.floristeriarosy.application.admin.port.out.AdminWritePort;
import com.floristeriarosy.application.auth.port.out.RevokeTokenFamilyPort;
import com.floristeriarosy.application.shared.port.out.AuditLogPort;
import com.floristeriarosy.domain.exception.admin.AdminNotFoundException;
import com.floristeriarosy.domain.model.admin.Admin;
import com.floristeriarosy.domain.model.admin.AdminRole;
import com.floristeriarosy.domain.model.admin.valueobject.AdminId;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link ResetAdminTotpService}: TOTP re-enrollment (admin.md, rule 3.5). */
@ExtendWith(MockitoExtension.class)
class ResetAdminTotpServiceTest {

  @Mock private AdminReadPort readPort;
  @Mock private AdminWritePort writePort;
  @Mock private RevokeTokenFamilyPort revokeTokenFamilyPort;
  @Mock private AuditLogPort auditLogPort;

  private ResetAdminTotpService service;

  private Admin enrolledAdmin() {
    return Admin.reconstitute(
        AdminId.newId(),
        "encrypted".getBytes(StandardCharsets.UTF_8),
        "hash".getBytes(StandardCharsets.UTF_8),
        "argon2-hash",
        AdminRole.ADMIN,
        "totp-secret".getBytes(StandardCharsets.UTF_8),
        true,
        42L,
        false,
        true,
        0L,
        null,
        null);
  }

  private void newService() {
    service = new ResetAdminTotpService(readPort, writePort, revokeTokenFamilyPort, auditLogPort);
  }

  private void stubSuccessfulSave() {
    when(writePort.save(any(Admin.class))).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void throwsWhenTheAdminDoesNotExist() {
    newService();
    UUID id = UUID.randomUUID();
    when(readPort.findById(AdminId.of(id))).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.execute(new ResetAdminTotpCommand(UUID.randomUUID(), id)))
        .isInstanceOf(AdminNotFoundException.class);
  }

  @Test
  void clearsTotpEnrollmentButLeavesThePasswordUntouched() {
    newService();
    stubSuccessfulSave();
    Admin admin = enrolledAdmin();
    String passwordHashBeforeReset = admin.passwordHash();
    when(readPort.findById(admin.id())).thenReturn(Optional.of(admin));

    service.execute(new ResetAdminTotpCommand(UUID.randomUUID(), admin.id().value()));

    assertThat(admin.totpSecretEncrypted()).isNull();
    assertThat(admin.totpEnabled()).isFalse();
    assertThat(admin.totpLastUsedStep()).isNull();
    assertThat(admin.passwordHash()).isEqualTo(passwordHashBeforeReset);
  }

  @Test
  void revokesEveryLiveSessionOfTheAdmin() {
    newService();
    stubSuccessfulSave();
    Admin admin = enrolledAdmin();
    when(readPort.findById(admin.id())).thenReturn(Optional.of(admin));

    service.execute(new ResetAdminTotpCommand(UUID.randomUUID(), admin.id().value()));

    verify(revokeTokenFamilyPort).revokeAllForSubject(admin.id().value());
  }

  @Test
  void neverRevokesSessionsWhenTheAdminDoesNotExist() {
    newService();
    UUID id = UUID.randomUUID();
    when(readPort.findById(AdminId.of(id))).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.execute(new ResetAdminTotpCommand(UUID.randomUUID(), id)))
        .isInstanceOf(AdminNotFoundException.class);

    verify(revokeTokenFamilyPort, never()).revokeAllForSubject(any());
  }
}
