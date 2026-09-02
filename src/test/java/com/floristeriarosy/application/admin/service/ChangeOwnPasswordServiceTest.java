package com.floristeriarosy.application.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.admin.command.ChangeOwnPasswordCommand;
import com.floristeriarosy.application.admin.port.out.AdminReadPort;
import com.floristeriarosy.application.admin.port.out.AdminWritePort;
import com.floristeriarosy.application.auth.port.out.PasswordHasherPort;
import com.floristeriarosy.application.shared.dto.AuditAction;
import com.floristeriarosy.application.shared.port.out.AuditLogPort;
import com.floristeriarosy.domain.exception.admin.AdminNotFoundException;
import com.floristeriarosy.domain.exception.admin.InvalidCurrentPasswordException;
import com.floristeriarosy.domain.exception.admin.PasswordUnchangedException;
import com.floristeriarosy.domain.model.admin.Admin;
import com.floristeriarosy.domain.model.admin.AdminRole;
import com.floristeriarosy.domain.model.admin.valueobject.AdminId;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link ChangeOwnPasswordService}: an admin replaces their own password (admin.md, rule 3.4). */
@ExtendWith(MockitoExtension.class)
class ChangeOwnPasswordServiceTest {

  @Mock private AdminReadPort readPort;
  @Mock private AdminWritePort writePort;
  @Mock private PasswordHasherPort passwordHasherPort;
  @Mock private AuditLogPort auditLogPort;

  private ChangeOwnPasswordService service;

  private Admin provisionalAdmin() {
    return Admin.create(
        AdminId.newId(),
        "encrypted".getBytes(StandardCharsets.UTF_8),
        "hash".getBytes(StandardCharsets.UTF_8),
        "provisional-hash",
        AdminRole.ADMIN);
  }

  private void newService() {
    service = new ChangeOwnPasswordService(readPort, writePort, passwordHasherPort, auditLogPort);
  }

  private void stubSuccessfulSave() {
    when(writePort.save(any(Admin.class))).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void throwsWhenTheAdminDoesNotExist() {
    newService();
    UUID id = UUID.randomUUID();
    when(readPort.findById(AdminId.of(id))).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> service.execute(new ChangeOwnPasswordCommand(id, "current", "NewPassword!234")))
        .isInstanceOf(AdminNotFoundException.class);
  }

  @Test
  void rejectsAWrongCurrentPassword() {
    newService();
    Admin admin = provisionalAdmin();
    when(readPort.findById(admin.id())).thenReturn(Optional.of(admin));
    when(passwordHasherPort.matches("wrong-current", admin.passwordHash())).thenReturn(false);

    assertThatThrownBy(
            () ->
                service.execute(
                    new ChangeOwnPasswordCommand(admin.id().value(), "wrong-current", "NewPassword!234")))
        .isInstanceOf(InvalidCurrentPasswordException.class);

    verify(writePort, never()).save(any());
  }

  @Test
  void rejectsANewPasswordEqualToTheCurrentOne() {
    newService();
    Admin admin = provisionalAdmin();
    when(readPort.findById(admin.id())).thenReturn(Optional.of(admin));
    when(passwordHasherPort.matches("SamePassword!234", admin.passwordHash())).thenReturn(true);

    assertThatThrownBy(
            () ->
                service.execute(
                    new ChangeOwnPasswordCommand(
                        admin.id().value(), "SamePassword!234", "SamePassword!234")))
        .isInstanceOf(PasswordUnchangedException.class);

    verify(writePort, never()).save(any());
  }

  @Test
  void setsTheNewPasswordAndClearsThePasswordChangeRequiredFlag() {
    newService();
    stubSuccessfulSave();
    Admin admin = provisionalAdmin();
    when(readPort.findById(admin.id())).thenReturn(Optional.of(admin));
    when(passwordHasherPort.matches("current-password", admin.passwordHash())).thenReturn(true);
    when(passwordHasherPort.hash("NewPassword!234")).thenReturn("new-argon2-hash");

    service.execute(
        new ChangeOwnPasswordCommand(admin.id().value(), "current-password", "NewPassword!234"));

    assertThat(admin.passwordHash()).isEqualTo("new-argon2-hash");
    assertThat(admin.passwordChangeRequired()).isFalse();
    verify(auditLogPort)
        .record(
            eq(admin.id().value()),
            eq(AuditAction.UPDATE),
            eq("admin_user"),
            eq(admin.id().value()),
            eq(List.of("passwordHash", "passwordChangeRequired")));
  }
}
