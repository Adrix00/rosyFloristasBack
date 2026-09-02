package com.floristeriarosy.application.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.admin.command.UpdateAdminCommand;
import com.floristeriarosy.application.admin.dto.AdminDto;
import com.floristeriarosy.application.admin.mapper.AdminDtoMapper;
import com.floristeriarosy.application.admin.port.out.AdminReadPort;
import com.floristeriarosy.application.admin.port.out.AdminWritePort;
import com.floristeriarosy.application.shared.port.out.AuditLogPort;
import com.floristeriarosy.application.shared.port.out.PiiCryptoPort;
import com.floristeriarosy.domain.exception.admin.AdminEmailAlreadyExistsException;
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

/** {@link UpdateAdminService}: full replace of email and role (admin.md, rules 3.3, 3.7). */
@ExtendWith(MockitoExtension.class)
class UpdateAdminServiceTest {

  private static final byte[] CURRENT_HASH = "current-hash".getBytes(StandardCharsets.UTF_8);

  @Mock private AdminReadPort readPort;
  @Mock private AdminWritePort writePort;
  @Mock private PiiCryptoPort piiCryptoPort;
  @Mock private AuditLogPort auditLogPort;

  private UpdateAdminService service;

  private Admin existingAdmin(AdminRole role) {
    return Admin.create(
        AdminId.newId(), "encrypted".getBytes(StandardCharsets.UTF_8), CURRENT_HASH, "hash", role);
  }

  private void newService() {
    service = new UpdateAdminService(readPort, writePort, piiCryptoPort, auditLogPort, new AdminDtoMapper(piiCryptoPort));
  }

  private void stubSuccessfulSave() {
    when(writePort.save(any(Admin.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(piiCryptoPort.decrypt(any())).thenReturn("new@rosy.test");
  }

  @Test
  void throwsWhenTheAdminDoesNotExist() {
    newService();
    UUID id = UUID.randomUUID();
    when(readPort.findById(AdminId.of(id))).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.execute(
                    new UpdateAdminCommand(UUID.randomUUID(), id, "new@rosy.test", AdminRole.ADMIN)))
        .isInstanceOf(AdminNotFoundException.class);
  }

  @Test
  void rejectsDemotingTheLastActiveOwnerToAdmin() {
    newService();
    Admin owner = existingAdmin(AdminRole.OWNER);
    when(readPort.findById(owner.id())).thenReturn(Optional.of(owner));
    when(readPort.countActiveOwnersForUpdate()).thenReturn(1L);

    assertThatThrownBy(
            () ->
                service.execute(
                    new UpdateAdminCommand(
                        UUID.randomUUID(), owner.id().value(), "new@rosy.test", AdminRole.ADMIN)))
        .isInstanceOf(LastOwnerCannotBeRemovedException.class);

    verify(writePort, never()).save(any());
  }

  @Test
  void allowsDemotingAnOwnerWhenAnotherActiveOwnerRemains() {
    newService();
    stubSuccessfulSave();
    Admin owner = existingAdmin(AdminRole.OWNER);
    when(readPort.findById(owner.id())).thenReturn(Optional.of(owner));
    when(readPort.countActiveOwnersForUpdate()).thenReturn(2L);
    when(piiCryptoPort.hmac("new@rosy.test")).thenReturn("new-hash".getBytes(StandardCharsets.UTF_8));
    when(readPort.findByEmailHash(any())).thenReturn(Optional.empty());
    when(piiCryptoPort.encrypt("new@rosy.test")).thenReturn("new-encrypted".getBytes(StandardCharsets.UTF_8));

    AdminDto dto =
        service.execute(
            new UpdateAdminCommand(
                UUID.randomUUID(), owner.id().value(), "new@rosy.test", AdminRole.ADMIN));

    assertThat(dto.role()).isEqualTo(AdminRole.ADMIN);
  }

  @Test
  void allowsDemotingAnInactiveOwnerRegardlessOfActiveOwnerCount() {
    newService();
    stubSuccessfulSave();
    Admin owner = existingAdmin(AdminRole.OWNER);
    owner.deactivate();
    when(readPort.findById(owner.id())).thenReturn(Optional.of(owner));
    when(piiCryptoPort.hmac("new@rosy.test")).thenReturn("new-hash".getBytes(StandardCharsets.UTF_8));
    when(readPort.findByEmailHash(any())).thenReturn(Optional.empty());
    when(piiCryptoPort.encrypt("new@rosy.test")).thenReturn("new-encrypted".getBytes(StandardCharsets.UTF_8));

    AdminDto dto =
        service.execute(
            new UpdateAdminCommand(
                UUID.randomUUID(), owner.id().value(), "new@rosy.test", AdminRole.ADMIN));

    assertThat(dto.role()).isEqualTo(AdminRole.ADMIN);
    verify(readPort, never()).countActiveOwnersForUpdate();
  }

  @Test
  void rejectsAnEmailAlreadyUsedByAnotherAdmin() {
    newService();
    Admin admin = existingAdmin(AdminRole.ADMIN);
    when(readPort.findById(admin.id())).thenReturn(Optional.of(admin));
    when(piiCryptoPort.hmac("taken@rosy.test")).thenReturn("other-hash".getBytes(StandardCharsets.UTF_8));
    Admin other = existingAdmin(AdminRole.ADMIN);
    when(readPort.findByEmailHash(any())).thenReturn(Optional.of(other));

    assertThatThrownBy(
            () ->
                service.execute(
                    new UpdateAdminCommand(
                        UUID.randomUUID(), admin.id().value(), "taken@rosy.test", AdminRole.ADMIN)))
        .isInstanceOf(AdminEmailAlreadyExistsException.class);
  }

  @Test
  void keepingTheSameEmailNeverChecksForAConflict() {
    newService();
    stubSuccessfulSave();
    Admin admin = existingAdmin(AdminRole.ADMIN);
    when(readPort.findById(admin.id())).thenReturn(Optional.of(admin));
    when(piiCryptoPort.hmac("unchanged@rosy.test")).thenReturn(CURRENT_HASH);
    when(piiCryptoPort.encrypt("unchanged@rosy.test")).thenReturn("re-encrypted".getBytes(StandardCharsets.UTF_8));

    service.execute(
        new UpdateAdminCommand(
            UUID.randomUUID(), admin.id().value(), "unchanged@rosy.test", AdminRole.ADMIN));

    verify(readPort, never()).findByEmailHash(any());
  }
}
