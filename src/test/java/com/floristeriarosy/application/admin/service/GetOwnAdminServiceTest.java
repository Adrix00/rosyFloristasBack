package com.floristeriarosy.application.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.admin.dto.AdminDto;
import com.floristeriarosy.application.admin.mapper.AdminDtoMapper;
import com.floristeriarosy.application.admin.port.out.AdminReadPort;
import com.floristeriarosy.application.admin.query.GetAdminQuery;
import com.floristeriarosy.application.shared.port.out.PiiCryptoPort;
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

/** {@link GetOwnAdminService}: {@code GET /admin/me} (any authenticated admin, on themselves). */
@ExtendWith(MockitoExtension.class)
class GetOwnAdminServiceTest {

  @Mock private AdminReadPort readPort;
  @Mock private PiiCryptoPort piiCryptoPort;

  private GetOwnAdminService service;

  @Test
  void returnsTheCallersOwnRecord() {
    service = new GetOwnAdminService(readPort, new AdminDtoMapper(piiCryptoPort));
    Admin admin =
        Admin.create(
            AdminId.newId(),
            "encrypted".getBytes(StandardCharsets.UTF_8),
            "hash".getBytes(StandardCharsets.UTF_8),
            "hash",
            AdminRole.ADMIN);
    when(readPort.findById(admin.id())).thenReturn(Optional.of(admin));
    when(piiCryptoPort.decrypt(admin.emailEncrypted())).thenReturn("admin@rosy.test");

    AdminDto dto = service.execute(new GetAdminQuery(admin.id().value()));

    assertThat(dto.id()).isEqualTo(admin.id().value());
    assertThat(dto.email()).isEqualTo("admin@rosy.test");
  }

  @Test
  void throwsWhenTheAdminDoesNotExist() {
    service = new GetOwnAdminService(readPort, new AdminDtoMapper(piiCryptoPort));
    UUID id = UUID.randomUUID();
    when(readPort.findById(AdminId.of(id))).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.execute(new GetAdminQuery(id)))
        .isInstanceOf(AdminNotFoundException.class);
  }
}
