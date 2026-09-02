package com.floristeriarosy.application.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.admin.command.ChangeAdminStatusCommand;
import com.floristeriarosy.application.admin.dto.AdminDto;
import com.floristeriarosy.application.admin.mapper.AdminDtoMapper;
import com.floristeriarosy.application.admin.port.out.AdminReadPort;
import com.floristeriarosy.application.admin.port.out.AdminWritePort;
import com.floristeriarosy.application.auth.port.out.RevokeTokenFamilyPort;
import com.floristeriarosy.application.shared.port.out.AuditLogPort;
import com.floristeriarosy.application.shared.port.out.PiiCryptoPort;
import com.floristeriarosy.domain.exception.admin.AdminNotFoundException;
import com.floristeriarosy.domain.exception.admin.LastOwnerCannotBeRemovedException;
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

/** {@link ChangeAdminStatusService}: activate/deactivate (admin.md, rules 3.6, 3.7). */
@ExtendWith(MockitoExtension.class)
class ChangeAdminStatusServiceTest {

  @Mock private AdminReadPort readPort;
  @Mock private AdminWritePort writePort;
  @Mock private RevokeTokenFamilyPort revokeTokenFamilyPort;
  @Mock private AuditLogPort auditLogPort;
  @Mock private PiiCryptoPort piiCryptoPort;

  private ChangeAdminStatusService service;

  private Admin existingAdmin(AdminRole role) {
    return Admin.create(
        AdminId.newId(),
        "encrypted".getBytes(StandardCharsets.UTF_8),
        "hash".getBytes(StandardCharsets.UTF_8),
        "hash",
        role);
  }

  private void newService() {
    service =
        new ChangeAdminStatusService(
            readPort, writePort, revokeTokenFamilyPort, auditLogPort, new AdminDtoMapper(piiCryptoPort));
  }

  private void stubSuccessfulSave() {
    when(writePort.save(any(Admin.class))).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void throwsWhenTheAdminDoesNotExist() {
    newService();
    UUID id = UUID.randomUUID();
    when(readPort.findById(AdminId.of(id))).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.execute(new ChangeAdminStatusCommand(UUID.randomUUID(), id, false)))
        .isInstanceOf(AdminNotFoundException.class);
  }

  @Test
  void rejectsDeactivatingTheLastActiveOwner() {
    newService();
    Admin owner = existingAdmin(AdminRole.OWNER);
    when(readPort.findById(owner.id())).thenReturn(Optional.of(owner));
    when(readPort.countActiveOwnersForUpdate()).thenReturn(1L);

    assertThatThrownBy(
            () ->
                service.execute(
                    new ChangeAdminStatusCommand(UUID.randomUUID(), owner.id().value(), false)))
        .isInstanceOf(LastOwnerCannotBeRemovedException.class);

    verify(writePort, never()).save(any());
    verify(revokeTokenFamilyPort, never()).revokeAllForSubject(any());
  }

  @Test
  void allowsDeactivatingAnOwnerWhenAnotherActiveOwnerRemains() {
    newService();
    stubSuccessfulSave();
    Admin owner = existingAdmin(AdminRole.OWNER);
    when(readPort.findById(owner.id())).thenReturn(Optional.of(owner));
    when(readPort.countActiveOwnersForUpdate()).thenReturn(2L);

    AdminDto dto =
        service.execute(new ChangeAdminStatusCommand(UUID.randomUUID(), owner.id().value(), false));

    assertThat(dto.active()).isFalse();
    verify(revokeTokenFamilyPort).revokeAllForSubject(owner.id().value());
  }

  @Test
  void deactivatingAPlainAdminNeedsNoOwnerCountCheck() {
    newService();
    stubSuccessfulSave();
    Admin admin = existingAdmin(AdminRole.ADMIN);
    when(readPort.findById(admin.id())).thenReturn(Optional.of(admin));

    AdminDto dto = service.execute(new ChangeAdminStatusCommand(UUID.randomUUID(), admin.id().value(), false));

    assertThat(dto.active()).isFalse();
    verify(readPort, never()).countActiveOwnersForUpdate();
    verify(revokeTokenFamilyPort).revokeAllForSubject(admin.id().value());
  }

  @Test
  void activatingNeverRevokesSessions() {
    newService();
    stubSuccessfulSave();
    Admin admin = existingAdmin(AdminRole.ADMIN);
    admin.deactivate();
    when(readPort.findById(admin.id())).thenReturn(Optional.of(admin));

    AdminDto dto = service.execute(new ChangeAdminStatusCommand(UUID.randomUUID(), admin.id().value(), true));

    assertThat(dto.active()).isTrue();
    verify(revokeTokenFamilyPort, never()).revokeAllForSubject(any());
  }
}
