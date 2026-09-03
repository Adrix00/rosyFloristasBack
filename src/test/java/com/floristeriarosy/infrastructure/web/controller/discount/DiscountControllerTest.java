package com.floristeriarosy.infrastructure.web.controller.discount;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.floristeriarosy.application.discount.command.EndDiscountCommand;
import com.floristeriarosy.application.discount.command.UpdateDiscountCommand;
import com.floristeriarosy.application.discount.dto.DiscountDto;
import com.floristeriarosy.application.discount.port.in.DeleteDiscountUseCase;
import com.floristeriarosy.application.discount.port.in.EndDiscountUseCase;
import com.floristeriarosy.application.discount.port.in.UpdateDiscountUseCase;
import com.floristeriarosy.domain.exception.discount.DiscountAlreadyStartedException;
import com.floristeriarosy.domain.exception.discount.DiscountNotEditableException;
import com.floristeriarosy.domain.exception.discount.DiscountNotFoundException;
import com.floristeriarosy.domain.exception.discount.DiscountPeriodInvalidException;
import com.floristeriarosy.domain.model.discount.DiscountState;
import com.floristeriarosy.application.auth.port.out.AccessTokenPort;
import com.floristeriarosy.application.shared.port.out.PiiCryptoPort;
import com.floristeriarosy.infrastructure.security.config.SecurityConfig;
import com.floristeriarosy.infrastructure.web.mapper.discount.DiscountWebMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DiscountController.class)
@Import({DiscountWebMapper.class, SecurityConfig.class})
class DiscountControllerTest {

  @Autowired private MockMvc mockMvc;

  // SecurityConfig's real filter chain now needs RateLimitFilter, which needs these two.
  @MockitoBean private PiiCryptoPort piiCryptoPort;
  @MockitoBean private AccessTokenPort accessTokenPort;

  @MockitoBean private UpdateDiscountUseCase updateDiscountUseCase;
  @MockitoBean private EndDiscountUseCase endDiscountUseCase;
  @MockitoBean private DeleteDiscountUseCase deleteDiscountUseCase;

  private DiscountDto dto(UUID id, DiscountState state) {
    Instant now = Instant.now();
    return new DiscountDto(
        id,
        UUID.randomUUID(),
        new BigDecimal("20.00"),
        new BigDecimal("15.00"),
        now.minusSeconds(3600),
        now.plusSeconds(3600),
        null,
        0,
        state,
        now,
        now);
  }

  @Test
  void updateReturns200() throws Exception {
    UUID id = UUID.randomUUID();
    when(updateDiscountUseCase.execute(any(UpdateDiscountCommand.class))).thenReturn(dto(id, DiscountState.ACTIVE));

    mockMvc
        .perform(
            put("/api/v1/discounts/" + id)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"salePrice\":12.00}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.salePrice").value(15.00));
  }

  @Test
  void updateALockedFieldReturns422WithDiscountNotEditableCode() throws Exception {
    UUID id = UUID.randomUUID();
    when(updateDiscountUseCase.execute(any(UpdateDiscountCommand.class)))
        .thenThrow(new DiscountNotEditableException("not editable"));

    mockMvc
        .perform(
            put("/api/v1/discounts/" + id)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"salePrice\":12.00}"))
        .andExpect(status().is(422))
        .andExpect(jsonPath("$.code").value("DISCOUNT_NOT_EDITABLE"));
  }

  @Test
  void updateAMissingDiscountReturns404WithDiscountNotFoundCode() throws Exception {
    UUID id = UUID.randomUUID();
    when(updateDiscountUseCase.execute(any(UpdateDiscountCommand.class)))
        .thenThrow(new DiscountNotFoundException("not found"));

    mockMvc
        .perform(
            put("/api/v1/discounts/" + id)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"salePrice\":12.00}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("DISCOUNT_NOT_FOUND"));
  }

  @Test
  void endReturns200() throws Exception {
    UUID id = UUID.randomUUID();
    when(endDiscountUseCase.execute(any(EndDiscountCommand.class))).thenReturn(dto(id, DiscountState.ENDED));

    mockMvc
        .perform(post("/api/v1/discounts/" + id + "/end").with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("ENDED"));
  }

  @Test
  void endAScheduledDiscountReturns422WithDiscountPeriodInvalidCode() throws Exception {
    UUID id = UUID.randomUUID();
    when(endDiscountUseCase.execute(any(EndDiscountCommand.class)))
        .thenThrow(new DiscountPeriodInvalidException("not started yet"));

    mockMvc
        .perform(post("/api/v1/discounts/" + id + "/end").with(csrf()))
        .andExpect(status().is(422))
        .andExpect(jsonPath("$.code").value("DISCOUNT_PERIOD_INVALID"));
  }

  @Test
  void deleteReturns204() throws Exception {
    UUID id = UUID.randomUUID();

    mockMvc.perform(delete("/api/v1/discounts/" + id).with(csrf())).andExpect(status().isNoContent());
  }

  @Test
  void deleteAnAlreadyStartedDiscountReturns409WithDiscountAlreadyStartedCode() throws Exception {
    UUID id = UUID.randomUUID();
    doThrow(new DiscountAlreadyStartedException("already started")).when(deleteDiscountUseCase).execute(any());

    mockMvc
        .perform(delete("/api/v1/discounts/" + id).with(csrf()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DISCOUNT_ALREADY_STARTED"));
  }
}
