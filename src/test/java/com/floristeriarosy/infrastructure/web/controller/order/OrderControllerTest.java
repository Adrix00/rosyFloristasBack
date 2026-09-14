package com.floristeriarosy.infrastructure.web.controller.order;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.floristeriarosy.application.auth.port.out.AccessTokenPort;
import com.floristeriarosy.application.order.dto.OrderDto;
import com.floristeriarosy.application.order.port.in.GetOrderUseCase;
import com.floristeriarosy.application.order.port.in.GetOrdersUseCase;
import com.floristeriarosy.application.shared.port.out.PiiCryptoPort;
import com.floristeriarosy.infrastructure.security.config.SecurityConfig;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** {@code GET /orders/{orderNumber}}, {@code GET /orders} — order.md §4, §6. */
@WebMvcTest(OrderController.class)
@Import({SecurityConfig.class})
class OrderControllerTest {

  @Autowired private MockMvc mockMvc;

  // SecurityConfig's filter chain needs these for RateLimitFilter.
  @MockitoBean private PiiCryptoPort piiCryptoPort;
  @MockitoBean private AccessTokenPort accessTokenPort;

  @MockitoBean private GetOrderUseCase getOrderUseCase;
  @MockitoBean private GetOrdersUseCase getOrdersUseCase;

  @Test
  void getOrderForAuthenticatedCustomerReturnsOk() throws Exception {
    when(getOrderUseCase.execute(any()))
        .thenReturn(
            new OrderDto(
                UUID.randomUUID(),
                "WEB-20260914-000001",
                "WEB",
                "PICKUP",
                "PENDING",
                List.of(
                    new OrderDto.Item(
                        UUID.randomUUID(),
                        "Rosa roja",
                        Map.of(),
                        new BigDecimal("25.00"),
                        null,
                        1,
                        new BigDecimal("25.00"))),
                new BigDecimal("25.00"),
                BigDecimal.ZERO,
                new BigDecimal("25.00"),
                Instant.now()));

    mockMvc
        .perform(
            get("/api/v1/orders/WEB-20260914-000001")
                .with(jwt().jwt(jwt -> jwt.subject(UUID.randomUUID().toString()).claim("subject_type", "CUSTOMER"))))
        .andExpect(status().isOk());
  }

  @Test
  void getOrderForGuestWithEmailReturnsOk() throws Exception {
    when(getOrderUseCase.execute(any()))
        .thenReturn(
            new OrderDto(
                UUID.randomUUID(),
                "WEB-20260914-000001",
                "WEB",
                "PICKUP",
                "PENDING",
                List.of(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                Instant.now()));

    mockMvc
        .perform(get("/api/v1/orders/WEB-20260914-000001").param("email", "ana@example.com"))
        .andExpect(status().isOk());
  }

  @Test
  void getOrderForGuestWithoutEmailReturnsNotFound() throws Exception {
    when(getOrderUseCase.execute(any()))
        .thenThrow(new com.floristeriarosy.domain.exception.order.OrderNotFoundException("Order not found"));

    mockMvc.perform(get("/api/v1/orders/WEB-20260914-000001")).andExpect(status().isNotFound());
  }

  @Test
  void getOrdersRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/v1/orders")).andExpect(status().isUnauthorized());
  }

  @Test
  void getOrdersForAuthenticatedCustomerReturnsOk() throws Exception {
    when(getOrdersUseCase.execute(any())).thenReturn(List.of());

    mockMvc
        .perform(
            get("/api/v1/orders")
                .with(jwt().jwt(jwt -> jwt.subject(UUID.randomUUID().toString()).claim("subject_type", "CUSTOMER"))))
        .andExpect(status().isOk());
  }
}
