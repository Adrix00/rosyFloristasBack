package com.floristeriarosy.infrastructure.web.controller.inventory;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.floristeriarosy.application.inventory.command.DismissInventoryAlertCommand;
import com.floristeriarosy.application.inventory.command.ResolveInventoryAlertCommand;
import com.floristeriarosy.application.inventory.dto.InventoryAlertDto;
import com.floristeriarosy.application.inventory.port.in.DismissInventoryAlertUseCase;
import com.floristeriarosy.application.inventory.port.in.GetInventoryAlertsUseCase;
import com.floristeriarosy.application.inventory.port.in.ResolveInventoryAlertUseCase;
import com.floristeriarosy.application.product.dto.PageResult;
import com.floristeriarosy.domain.exception.inventory.InventoryAlertNotFoundException;
import com.floristeriarosy.domain.exception.inventory.InventoryAlertNotOpenException;
import com.floristeriarosy.domain.model.inventory.InventoryAlertStatus;
import com.floristeriarosy.domain.model.inventory.InventoryAlertType;
import com.floristeriarosy.application.auth.port.out.AccessTokenPort;
import com.floristeriarosy.application.shared.port.out.PiiCryptoPort;
import com.floristeriarosy.infrastructure.security.config.SecurityConfig;
import com.floristeriarosy.infrastructure.web.mapper.inventory.InventoryAlertWebMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** {@code /api/v1/inventory/alerts} (inventory.md, section 4, section 5, section 9; ADR-013). */
@WebMvcTest(InventoryAlertController.class)
@Import({InventoryAlertWebMapper.class, SecurityConfig.class})
class InventoryAlertControllerTest {

  @Autowired private MockMvc mockMvc;

  // SecurityConfig's real filter chain now needs RateLimitFilter, which needs these two.
  @MockitoBean private PiiCryptoPort piiCryptoPort;
  @MockitoBean private AccessTokenPort accessTokenPort;

  @MockitoBean private GetInventoryAlertsUseCase getInventoryAlertsUseCase;
  @MockitoBean private ResolveInventoryAlertUseCase resolveInventoryAlertUseCase;
  @MockitoBean private DismissInventoryAlertUseCase dismissInventoryAlertUseCase;

  private InventoryAlertDto dto(UUID id, InventoryAlertStatus status) {
    Instant now = Instant.now();
    return new InventoryAlertDto(
        id, InventoryAlertType.LOW_STOCK, UUID.randomUUID(), "Ramo", 2, 5, status, null, null, now);
  }

  @Test
  void getAllReturns200() throws Exception {
    UUID id = UUID.randomUUID();
    when(getInventoryAlertsUseCase.execute(any()))
        .thenReturn(new PageResult<>(List.of(dto(id, InventoryAlertStatus.OPEN)), 1, 0, 20));

    mockMvc
        .perform(get("/api/v1/inventory/alerts").param("type", "LOW_STOCK").param("status", "OPEN"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));
  }

  @Test
  void resolveReturns200() throws Exception {
    UUID id = UUID.randomUUID();
    when(resolveInventoryAlertUseCase.execute(any(ResolveInventoryAlertCommand.class)))
        .thenReturn(dto(id, InventoryAlertStatus.RESOLVED));

    mockMvc
        .perform(
            patch("/api/v1/inventory/alerts/" + id + "/resolve")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\":\"repuesto\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("RESOLVED"));
  }

  @Test
  void resolveWithoutCsrfIsForbidden() throws Exception {
    UUID id = UUID.randomUUID();

    mockMvc
        .perform(
            patch("/api/v1/inventory/alerts/" + id + "/resolve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void resolveAMissingAlertReturns404() throws Exception {
    UUID id = UUID.randomUUID();
    when(resolveInventoryAlertUseCase.execute(any(ResolveInventoryAlertCommand.class)))
        .thenThrow(new InventoryAlertNotFoundException("not found"));

    mockMvc
        .perform(
            patch("/api/v1/inventory/alerts/" + id + "/resolve")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("INVENTORY_ALERT_NOT_FOUND"));
  }

  @Test
  void resolveAnAlreadyClosedAlertReturns409() throws Exception {
    UUID id = UUID.randomUUID();
    when(resolveInventoryAlertUseCase.execute(any(ResolveInventoryAlertCommand.class)))
        .thenThrow(new InventoryAlertNotOpenException("not open"));

    mockMvc
        .perform(
            patch("/api/v1/inventory/alerts/" + id + "/resolve")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INVENTORY_ALERT_NOT_OPEN"));
  }

  @Test
  void resolveWithANoteLongerThanFiveHundredCharsReturns422() throws Exception {
    UUID id = UUID.randomUUID();
    String longNote = "n".repeat(501);

    mockMvc
        .perform(
            patch("/api/v1/inventory/alerts/" + id + "/resolve")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\":\"" + longNote + "\"}"))
        .andExpect(status().is(422))
        .andExpect(jsonPath("$.code").value("INVENTORY_VALIDATION_FAILED"));
  }

  @Test
  void dismissReturns200() throws Exception {
    UUID id = UUID.randomUUID();
    when(dismissInventoryAlertUseCase.execute(any(DismissInventoryAlertCommand.class)))
        .thenReturn(dto(id, InventoryAlertStatus.DISMISSED));

    mockMvc
        .perform(
            patch("/api/v1/inventory/alerts/" + id + "/dismiss")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\":\"umbral conservador\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DISMISSED"));
  }

  @Test
  void dismissAMissingAlertReturns404() throws Exception {
    UUID id = UUID.randomUUID();
    when(dismissInventoryAlertUseCase.execute(any(DismissInventoryAlertCommand.class)))
        .thenThrow(new InventoryAlertNotFoundException("not found"));

    mockMvc
        .perform(
            patch("/api/v1/inventory/alerts/" + id + "/dismiss")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("INVENTORY_ALERT_NOT_FOUND"));
  }

  @Test
  void dismissAnAlreadyClosedAlertReturns409() throws Exception {
    UUID id = UUID.randomUUID();
    when(dismissInventoryAlertUseCase.execute(any(DismissInventoryAlertCommand.class)))
        .thenThrow(new InventoryAlertNotOpenException("not open"));

    mockMvc
        .perform(
            patch("/api/v1/inventory/alerts/" + id + "/dismiss")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INVENTORY_ALERT_NOT_OPEN"));
  }
}
