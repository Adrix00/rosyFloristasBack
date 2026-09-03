package com.floristeriarosy.infrastructure.web.controller.discount;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import com.floristeriarosy.application.discount.command.CreateDiscountCommand;
import com.floristeriarosy.application.discount.dto.DiscountDto;
import com.floristeriarosy.application.discount.port.in.CreateDiscountUseCase;
import com.floristeriarosy.application.discount.port.in.GetProductDiscountsUseCase;
import com.floristeriarosy.application.discount.query.GetProductDiscountsQuery;
import com.floristeriarosy.domain.exception.discount.DiscountOverlapException;
import com.floristeriarosy.domain.exception.product.ProductNotFoundException;
import com.floristeriarosy.domain.model.discount.DiscountState;
import com.floristeriarosy.application.auth.port.out.AccessTokenPort;
import com.floristeriarosy.application.shared.port.out.PiiCryptoPort;
import com.floristeriarosy.infrastructure.security.config.SecurityConfig;
import com.floristeriarosy.infrastructure.web.mapper.discount.DiscountWebMapper;
import java.math.BigDecimal;
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

@WebMvcTest(ProductDiscountController.class)
@Import({DiscountWebMapper.class, SecurityConfig.class})
class ProductDiscountControllerTest {

  @Autowired private MockMvc mockMvc;

  // SecurityConfig's real filter chain now needs RateLimitFilter, which needs these two.
  @MockitoBean private PiiCryptoPort piiCryptoPort;
  @MockitoBean private AccessTokenPort accessTokenPort;

  @MockitoBean private CreateDiscountUseCase createDiscountUseCase;
  @MockitoBean private GetProductDiscountsUseCase getProductDiscountsUseCase;

  private DiscountDto dto(UUID id, UUID productId, DiscountState state) {
    Instant now = Instant.now();
    return new DiscountDto(
        id,
        productId,
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
  void createReturns201() throws Exception {
    UUID productId = UUID.randomUUID();
    UUID id = UUID.randomUUID();
    when(createDiscountUseCase.execute(any(CreateDiscountCommand.class)))
        .thenReturn(dto(id, productId, DiscountState.SCHEDULED));

    mockMvc
        .perform(
            post("/api/v1/products/" + productId + "/discounts")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"salePrice\":15.00,\"startsAt\":\""
                        + Instant.now().plusSeconds(3600)
                        + "\",\"endsAt\":\""
                        + Instant.now().plusSeconds(7200)
                        + "\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.salePrice").value(15.00));
  }

  @Test
  void createWithoutSalePriceReturns422WithDiscountValidationCode() throws Exception {
    UUID productId = UUID.randomUUID();

    mockMvc
        .perform(
            post("/api/v1/products/" + productId + "/discounts")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"startsAt\":\""
                        + Instant.now().plusSeconds(3600)
                        + "\",\"endsAt\":\""
                        + Instant.now().plusSeconds(7200)
                        + "\"}"))
        .andExpect(status().is(422))
        .andExpect(jsonPath("$.code").value("DISCOUNT_VALIDATION_FAILED"));
  }

  @Test
  void createOnAMissingProductReturns404WithProductNotFoundCode() throws Exception {
    UUID productId = UUID.randomUUID();
    when(createDiscountUseCase.execute(any(CreateDiscountCommand.class)))
        .thenThrow(new ProductNotFoundException("not found"));

    mockMvc
        .perform(
            post("/api/v1/products/" + productId + "/discounts")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"salePrice\":15.00,\"startsAt\":\""
                        + Instant.now().plusSeconds(3600)
                        + "\",\"endsAt\":\""
                        + Instant.now().plusSeconds(7200)
                        + "\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
  }

  @Test
  void createOverlappingAnotherDiscountReturns409WithDiscountOverlapCode() throws Exception {
    UUID productId = UUID.randomUUID();
    when(createDiscountUseCase.execute(any(CreateDiscountCommand.class)))
        .thenThrow(new DiscountOverlapException("overlaps"));

    mockMvc
        .perform(
            post("/api/v1/products/" + productId + "/discounts")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"salePrice\":15.00,\"startsAt\":\""
                        + Instant.now().plusSeconds(3600)
                        + "\",\"endsAt\":\""
                        + Instant.now().plusSeconds(7200)
                        + "\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DISCOUNT_OVERLAP"));
  }

  @Test
  void getAllReturns200WithTheProductsDiscountHistory() throws Exception {
    UUID productId = UUID.randomUUID();
    when(getProductDiscountsUseCase.execute(any(GetProductDiscountsQuery.class)))
        .thenReturn(List.of(dto(UUID.randomUUID(), productId, DiscountState.ACTIVE)));

    mockMvc
        .perform(get("/api/v1/products/" + productId + "/discounts"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].state").value("ACTIVE"));
  }

  @Test
  void getAllOnAMissingProductReturns404WithProductNotFoundCode() throws Exception {
    UUID productId = UUID.randomUUID();
    when(getProductDiscountsUseCase.execute(any(GetProductDiscountsQuery.class)))
        .thenThrow(new ProductNotFoundException("not found"));

    mockMvc
        .perform(get("/api/v1/products/" + productId + "/discounts"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
  }
}
