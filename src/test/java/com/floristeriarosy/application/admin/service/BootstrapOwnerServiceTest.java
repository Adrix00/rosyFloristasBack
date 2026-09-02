package com.floristeriarosy.application.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.admin.command.BootstrapOwnerCommand;
import com.floristeriarosy.application.admin.port.out.AdminReadPort;
import com.floristeriarosy.application.admin.port.out.AdminWritePort;
import com.floristeriarosy.application.auth.port.out.PasswordHasherPort;
import com.floristeriarosy.application.shared.port.out.PiiCryptoPort;
import com.floristeriarosy.domain.model.admin.Admin;
import com.floristeriarosy.domain.model.admin.AdminRole;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link BootstrapOwnerService}: creates the first {@code OWNER} at startup (admin.md, rule 3.9). */
@ExtendWith(MockitoExtension.class)
class BootstrapOwnerServiceTest {

  @Mock private AdminReadPort readPort;
  @Mock private AdminWritePort writePort;
  @Mock private PiiCryptoPort piiCryptoPort;
  @Mock private PasswordHasherPort passwordHasherPort;

  private BootstrapOwnerService service;

  private void newService() {
    service = new BootstrapOwnerService(readPort, writePort, piiCryptoPort, passwordHasherPort);
  }

  @Test
  void doesNothingWhenAnActiveOwnerAlreadyExists() {
    newService();
    when(readPort.countActiveOwners()).thenReturn(1L);

    service.execute(new BootstrapOwnerCommand("owner@rosy.test", "Provisional!234"));

    verify(writePort, never()).save(any());
  }

  @Test
  void doesNotThrowWhenNoOwnerExistsAndNoCredentialsAreConfigured() {
    newService();
    when(readPort.countActiveOwners()).thenReturn(0L);

    assertThatCode(() -> service.execute(new BootstrapOwnerCommand("", ""))).doesNotThrowAnyException();

    verify(writePort, never()).save(any());
  }

  @Test
  void doesNotThrowWhenCredentialsAreNull() {
    newService();
    when(readPort.countActiveOwners()).thenReturn(0L);

    assertThatCode(() -> service.execute(new BootstrapOwnerCommand(null, null))).doesNotThrowAnyException();

    verify(writePort, never()).save(any());
  }

  @Test
  void createsTheFirstOwnerFromTheConfiguredCredentials() {
    newService();
    when(readPort.countActiveOwners()).thenReturn(0L);
    when(piiCryptoPort.encrypt("owner@rosy.test")).thenReturn("encrypted".getBytes(StandardCharsets.UTF_8));
    when(piiCryptoPort.hmac("owner@rosy.test")).thenReturn("hash".getBytes(StandardCharsets.UTF_8));
    when(passwordHasherPort.hash("Provisional!234")).thenReturn("argon2-hash");
    when(writePort.save(any(Admin.class))).thenAnswer(invocation -> invocation.getArgument(0));

    service.execute(new BootstrapOwnerCommand(" Owner@Rosy.test ", "Provisional!234"));

    ArgumentCaptor<Admin> captor = ArgumentCaptor.forClass(Admin.class);
    verify(writePort).save(captor.capture());
    Admin created = captor.getValue();
    assertThat(created.role()).isEqualTo(AdminRole.OWNER);
    assertThat(created.active()).isTrue();
    assertThat(created.totpEnabled()).isFalse();
    assertThat(created.passwordChangeRequired()).isTrue();
    verify(piiCryptoPort).encrypt(eq("owner@rosy.test"));
    verify(piiCryptoPort).hmac(eq("owner@rosy.test"));
  }
}
