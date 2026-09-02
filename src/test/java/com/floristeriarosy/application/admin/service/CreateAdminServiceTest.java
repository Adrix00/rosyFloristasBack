package com.floristeriarosy.application.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.admin.command.CreateAdminCommand;
import com.floristeriarosy.application.admin.dto.AdminDto;
import com.floristeriarosy.application.admin.mapper.AdminDtoMapper;
import com.floristeriarosy.application.admin.port.out.AdminReadPort;
import com.floristeriarosy.application.admin.port.out.AdminWritePort;
import com.floristeriarosy.application.auth.port.out.PasswordHasherPort;
import com.floristeriarosy.application.shared.dto.AuditAction;
import com.floristeriarosy.application.shared.port.out.AuditLogPort;
import com.floristeriarosy.application.shared.port.out.PiiCryptoPort;
import com.floristeriarosy.domain.exception.admin.AdminEmailAlreadyExistsException;
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

/** {@link CreateAdminService}: alta with a provisional password (admin.md, rule 3.2). */
@ExtendWith(MockitoExtension.class)
class CreateAdminServiceTest {

  @Mock private AdminReadPort readPort;
  @Mock private AdminWritePort writePort;
  @Mock private PiiCryptoPort piiCryptoPort;
  @Mock private PasswordHasherPort passwordHasherPort;
  @Mock private AuditLogPort auditLogPort;

  private CreateAdminService service;

  @Test
  void createsAnAdminBornActiveWithoutTotpAndRequiringAPasswordChange() {
    service =
        new CreateAdminService(
            readPort, writePort, piiCryptoPort, passwordHasherPort, auditLogPort, new AdminDtoMapper(piiCryptoPort));
    UUID actorId = UUID.randomUUID();
    when(piiCryptoPort.hmac("new@rosy.test")).thenReturn("hash".getBytes(StandardCharsets.UTF_8));
    when(readPort.findByEmailHash(any())).thenReturn(Optional.empty());
    when(piiCryptoPort.encrypt("new@rosy.test")).thenReturn("encrypted".getBytes(StandardCharsets.UTF_8));
    when(passwordHasherPort.hash("Provisional!234")).thenReturn("argon2-hash");
    when(writePort.save(any(Admin.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(piiCryptoPort.decrypt(any())).thenReturn("new@rosy.test");

    AdminDto dto =
        service.execute(
            new CreateAdminCommand(actorId, " New@Rosy.test ", "Provisional!234", AdminRole.ADMIN));

    assertThat(dto.active()).isTrue();
    assertThat(dto.totpEnabled()).isFalse();
    assertThat(dto.passwordChangeRequired()).isTrue();
    assertThat(dto.role()).isEqualTo(AdminRole.ADMIN);
    verify(auditLogPort)
        .record(
            eq(actorId),
            eq(AuditAction.CREATE),
            eq("admin_user"),
            any(UUID.class),
            eq(List.of("email", "role", "passwordHash")));
  }

  @Test
  void rejectsAnEmailAlreadyUsedByAnotherAdmin() {
    service =
        new CreateAdminService(
            readPort, writePort, piiCryptoPort, passwordHasherPort, auditLogPort, new AdminDtoMapper(piiCryptoPort));
    when(piiCryptoPort.hmac("taken@rosy.test")).thenReturn("hash".getBytes(StandardCharsets.UTF_8));
    when(readPort.findByEmailHash(any()))
        .thenReturn(
            Optional.of(
                Admin.create(
                    AdminId.newId(),
                    "e".getBytes(StandardCharsets.UTF_8),
                    "hash".getBytes(StandardCharsets.UTF_8),
                    "hash",
                    AdminRole.ADMIN)));

    assertThatThrownBy(
            () ->
                service.execute(
                    new CreateAdminCommand(
                        UUID.randomUUID(), "taken@rosy.test", "Provisional!234", AdminRole.ADMIN)))
        .isInstanceOf(AdminEmailAlreadyExistsException.class);

    verify(writePort, never()).save(any());
    verify(auditLogPort, never()).record(any(), any(), any(), any(), any());
  }
}
