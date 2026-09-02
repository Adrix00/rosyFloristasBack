package com.floristeriarosy.application.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.admin.command.ResetAdminPasswordCommand;
import com.floristeriarosy.application.admin.dto.PasswordResetResult;
import com.floristeriarosy.application.admin.port.out.AdminReadPort;
import com.floristeriarosy.application.admin.port.out.AdminWritePort;
import com.floristeriarosy.application.auth.port.out.PasswordHasherPort;
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

/** {@link ResetAdminPasswordService}: the {@code OWNER} fixes a new provisional password (admin.md, rule 3.4). */
@ExtendWith(MockitoExtension.class)
class ResetAdminPasswordServiceTest {

  @Mock private AdminReadPort readPort;
  @Mock private AdminWritePort writePort;
  @Mock private PasswordHasherPort passwordHasherPort;
  @Mock private RevokeTokenFamilyPort revokeTokenFamilyPort;
  @Mock private AuditLogPort auditLogPort;

  private ResetAdminPasswordService service;

  private Admin existingAdmin() {
    Admin admin =
        Admin.create(
            AdminId.newId(),
            "encrypted".getBytes(StandardCharsets.UTF_8),
            "hash".getBytes(StandardCharsets.UTF_8),
            "old-hash",
            AdminRole.ADMIN);
    admin.changeOwnPassword("chosen-by-admin");
    return admin;
  }

  private void newService() {
    service = new ResetAdminPasswordService(readPort, writePort, passwordHasherPort, revokeTokenFamilyPort, auditLogPort);
  }

  private void stubSuccessfulSave() {
    when(writePort.save(any(Admin.class))).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void throwsWhenTheAdminDoesNotExist() {
    newService();
    UUID id = UUID.randomUUID();
    when(readPort.findById(AdminId.of(id))).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.execute(new ResetAdminPasswordCommand(UUID.randomUUID(), id)))
        .isInstanceOf(AdminNotFoundException.class);
  }

  @Test
  void generatesAProvisionalPasswordAndForcesAChangeOnNextLogin() {
    newService();
    stubSuccessfulSave();
    Admin admin = existingAdmin();
    when(readPort.findById(admin.id())).thenReturn(Optional.of(admin));
    when(passwordHasherPort.hash(anyString())).thenReturn("new-argon2-hash");

    PasswordResetResult result =
        service.execute(new ResetAdminPasswordCommand(UUID.randomUUID(), admin.id().value()));

    assertThat(result.temporaryPassword()).isNotBlank();
    assertThat(admin.passwordHash()).isEqualTo("new-argon2-hash");
    assertThat(admin.passwordChangeRequired()).isTrue();
  }

  @Test
  void revokesEveryLiveSessionOfTheAdmin() {
    newService();
    stubSuccessfulSave();
    Admin admin = existingAdmin();
    when(readPort.findById(admin.id())).thenReturn(Optional.of(admin));
    when(passwordHasherPort.hash(anyString())).thenReturn("new-argon2-hash");

    service.execute(new ResetAdminPasswordCommand(UUID.randomUUID(), admin.id().value()));

    verify(revokeTokenFamilyPort).revokeAllForSubject(admin.id().value());
  }

  @Test
  void neverRevokesSessionsWhenTheAdminDoesNotExist() {
    newService();
    UUID id = UUID.randomUUID();
    when(readPort.findById(AdminId.of(id))).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.execute(new ResetAdminPasswordCommand(UUID.randomUUID(), id)))
        .isInstanceOf(AdminNotFoundException.class);

    verify(revokeTokenFamilyPort, never()).revokeAllForSubject(any());
  }
}
