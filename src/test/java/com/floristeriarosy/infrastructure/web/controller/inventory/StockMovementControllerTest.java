package com.floristeriarosy.infrastructure.web.controller.inventory;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.floristeriarosy.application.inventory.command.RegisterAdjustmentCommand;
import com.floristeriarosy.application.inventory.command.RegisterWasteCommand;
import com.floristeriarosy.application.inventory.dto.StockMovementDto;
import com.floristeriarosy.application.inventory.port.in.GetStockMovementsUseCase;
import com.floristeriarosy.application.inventory.port.in.RegisterAdjustmentUseCase;
import com.floristeriarosy.application.inventory.port.in.RegisterWasteUseCase;
import com.floristeriarosy.application.product.dto.PageResult;
import com.floristeriarosy.domain.exception.inventory.InventoryInsufficientStockException;
import com.floristeriarosy.domain.exception.inventory.InventoryNotManagedException;
import com.floristeriarosy.domain.model.inventory.StockMovementType;
import com.floristeriarosy.application.auth.port.out.AccessTokenPort;
import com.floristeriarosy.application.shared.port.out.PiiCryptoPort;
import com.floristeriarosy.infrastructure.security.config.SecurityConfig;
import com.floristeriarosy.infrastructure.web.mapper.inventory.StockMovementWebMapper;
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

/**
 * {@code /api/v1/products/{id}/stock-movements} (inventory.md, section 4, section 5, section 9).
 */
@WebMvcTest(StockMovementController.class)
@Import({StockMovementWebMapper.class, SecurityConfig.class})
class StockMovementControllerTest {

  @Autowired private MockMvc mockMvc;

  // SecurityConfig's real filter chain now needs RateLimitFilter, which needs these two.
  @MockitoBean private PiiCryptoPort piiCryptoPort;
  @MockitoBean private AccessTokenPort accessTokenPort;

  @MockitoBean private GetStockMovementsUseCase getStockMovementsUseCase;
  @MockitoBean private RegisterWasteUseCase registerWasteUseCase;
  @MockitoBean private RegisterAdjustmentUseCase registerAdjustmentUseCase;

  private StockMovementDto dto(StockMovementType type, int quantity, int resultingStock) {
    return new StockMovementDto(
        UUID.randomUUID(), UUID.randomUUID(), type, quantity, resultingStock, null, "nota", Instant.now());
  }

  @Test
  void getHistoryReturns200() throws Exception {
    UUID id = UUID.randomUUID();
    when(getStockMovementsUseCase.execute(any()))
        .thenReturn(new PageResult<>(List.of(dto(StockMovementType.SALE, -1, 9)), 1, 0, 20));

    mockMvc
        .perform(get("/api/v1/products/" + id + "/stock-movements"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));
  }

  @Test
  void registerWasteReturns201() throws Exception {
    UUID id = UUID.randomUUID();
    when(registerWasteUseCase.execute(any(RegisterWasteCommand.class)))
        .thenReturn(dto(StockMovementType.WASTE, -2, 8));

    mockMvc
        .perform(
            post("/api/v1/products/" + id + "/stock-movements/waste")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":2,\"note\":\"rota\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.type").value("WASTE"))
        .andExpect(jsonPath("$.quantity").value(-2));
  }

  @Test
  void registerWasteWithoutCsrfIsForbidden() throws Exception {
    UUID id = UUID.randomUUID();

    mockMvc
        .perform(
            post("/api/v1/products/" + id + "/stock-movements/waste")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":2,\"note\":\"rota\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void registerWasteWithAZeroQuantityReturns422() throws Exception {
    UUID id = UUID.randomUUID();

    mockMvc
        .perform(
            post("/api/v1/products/" + id + "/stock-movements/waste")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":0,\"note\":\"rota\"}"))
        .andExpect(status().is(422))
        .andExpect(jsonPath("$.code").value("INVENTORY_VALIDATION_FAILED"));
  }

  @Test
  void registerWasteWithoutANoteReturns422() throws Exception {
    UUID id = UUID.randomUUID();

    mockMvc
        .perform(
            post("/api/v1/products/" + id + "/stock-movements/waste")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":2,\"note\":\"\"}"))
        .andExpect(status().is(422))
        .andExpect(jsonPath("$.code").value("INVENTORY_VALIDATION_FAILED"));
  }

  @Test
  void registerWasteOnAnUnmanagedProductReturns409() throws Exception {
    UUID id = UUID.randomUUID();
    when(registerWasteUseCase.execute(any(RegisterWasteCommand.class)))
        .thenThrow(new InventoryNotManagedException("not managed"));

    mockMvc
        .perform(
            post("/api/v1/products/" + id + "/stock-movements/waste")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":2,\"note\":\"rota\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INVENTORY_NOT_MANAGED"));
  }

  @Test
  void registerWasteBeyondAvailableStockReturns409() throws Exception {
    UUID id = UUID.randomUUID();
    when(registerWasteUseCase.execute(any(RegisterWasteCommand.class)))
        .thenThrow(new InventoryInsufficientStockException("insufficient"));

    mockMvc
        .perform(
            post("/api/v1/products/" + id + "/stock-movements/waste")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":999,\"note\":\"rota\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INVENTORY_INSUFFICIENT_STOCK"));
  }

  @Test
  void registerAdjustmentReturns201() throws Exception {
    UUID id = UUID.randomUUID();
    when(registerAdjustmentUseCase.execute(any(RegisterAdjustmentCommand.class)))
        .thenReturn(dto(StockMovementType.ADJUSTMENT, 5, 15));

    mockMvc
        .perform(
            post("/api/v1/products/" + id + "/stock-movements/adjustment")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":5,\"note\":\"recuento\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.type").value("ADJUSTMENT"));
  }

  @Test
  void registerAdjustmentWithAZeroQuantityReturns422() throws Exception {
    UUID id = UUID.randomUUID();

    mockMvc
        .perform(
            post("/api/v1/products/" + id + "/stock-movements/adjustment")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":0,\"note\":\"recuento\"}"))
        .andExpect(status().is(422))
        .andExpect(jsonPath("$.code").value("INVENTORY_VALIDATION_FAILED"));
  }

  @Test
  void registerAdjustmentWithoutANoteReturns422() throws Exception {
    UUID id = UUID.randomUUID();

    mockMvc
        .perform(
            post("/api/v1/products/" + id + "/stock-movements/adjustment")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":5,\"note\":\"\"}"))
        .andExpect(status().is(422))
        .andExpect(jsonPath("$.code").value("INVENTORY_VALIDATION_FAILED"));
  }
}
