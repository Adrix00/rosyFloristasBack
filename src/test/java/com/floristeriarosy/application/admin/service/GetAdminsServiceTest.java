package com.floristeriarosy.application.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.floristeriarosy.application.admin.dto.AdminDto;
import com.floristeriarosy.application.admin.mapper.AdminDtoMapper;
import com.floristeriarosy.application.admin.port.out.AdminReadPort;
import com.floristeriarosy.application.admin.query.GetAdminsQuery;
import com.floristeriarosy.application.shared.port.out.PiiCryptoPort;
import com.floristeriarosy.domain.model.admin.Admin;
import com.floristeriarosy.domain.model.admin.AdminRole;
import com.floristeriarosy.domain.model.admin.valueobject.AdminId;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link GetAdminsService}: lists admins, optionally filtered by {@code active}/{@code role}. */
@ExtendWith(MockitoExtension.class)
class GetAdminsServiceTest {

  @Mock private AdminReadPort readPort;
  @Mock private PiiCryptoPort piiCryptoPort;

  private GetAdminsService service;

  @Test
  void passesTheFiltersThroughAndMapsEachResult() {
    service = new GetAdminsService(readPort, new AdminDtoMapper(piiCryptoPort));
    Admin admin =
        Admin.create(
            AdminId.newId(),
            "encrypted".getBytes(StandardCharsets.UTF_8),
            "hash".getBytes(StandardCharsets.UTF_8),
            "hash",
            AdminRole.ADMIN);
    when(readPort.findAll(eq(true), eq(AdminRole.ADMIN))).thenReturn(List.of(admin));

    List<AdminDto> result = service.execute(new GetAdminsQuery(true, AdminRole.ADMIN));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).id()).isEqualTo(admin.id().value());
  }

  @Test
  void returnsAnEmptyListWhenNothingMatches() {
    service = new GetAdminsService(readPort, new AdminDtoMapper(piiCryptoPort));
    when(readPort.findAll(null, null)).thenReturn(List.of());

    List<AdminDto> result = service.execute(new GetAdminsQuery(null, null));

    assertThat(result).isEmpty();
  }
}
