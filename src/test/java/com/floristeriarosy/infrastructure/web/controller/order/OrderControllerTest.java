package com.floristeriarosy.infrastructure.web.controller.order;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
import com.floristeriarosy.application.auth.port.out.AccessTokenPort;
import com.floristeriarosy.application.order.dto.OrderDto;
import com.floristeriarosy.application.order.port.in.CancelOrderUseCase;
import com.floristeriarosy.application.order.port.in.CompleteOrderUseCase;
import com.floristeriarosy.application.order.port.in.GetOrderUseCase;
import com.floristeriarosy.application.order.port.in.GetOrdersUseCase;
import com.floristeriarosy.application.order.port.in.PrepareOrderUseCase;
import com.floristeriarosy.application.order.port.in.RejectOrderUseCase;
import com.floristeriarosy.application.order.port.in.ShipOrderUseCase;
import com.floristeriarosy.application.shared.port.out.PiiCryptoPort;
import com.floristeriarosy.domain.exception.payment.PaymentGatewayException;
import com.floristeriarosy.infrastructure.security.config.SecurityConfig;
import com.floristeriarosy.infrastructure.web.request.order.CancelOrderRequest;
import com.floristeriarosy.infrastructure.web.request.order.RejectOrderRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
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
  @MockitoBean private PrepareOrderUseCase prepareOrderUseCase;
  @MockitoBean private ShipOrderUseCase shipOrderUseCase;
  @MockitoBean private CompleteOrderUseCase completeOrderUseCase;
  @MockitoBean private RejectOrderUseCase rejectOrderUseCase;
  @MockitoBean private CancelOrderUseCase cancelOrderUseCase;

  private final ObjectMapper objectMapper = new ObjectMapper();

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

  @Test
  void prepareRequiresAdminJwtAndCsrf() throws Exception {
    UUID orderId = UUID.randomUUID();
    when(prepareOrderUseCase.execute(any())).thenReturn(emptyOrderDto(orderId, "PREPARING"));

    mockMvc
        .perform(
            post("/api/v1/orders/{id}/prepare", orderId)
                .with(jwt().jwt(jwt -> jwt.subject(UUID.randomUUID().toString()).claim("subject_type", "ADMIN")))
                .with(csrf()))
        .andExpect(status().isOk());
  }

  @Test
  void shipRequiresAdminJwtAndCsrf() throws Exception {
    UUID orderId = UUID.randomUUID();
    when(shipOrderUseCase.execute(any())).thenReturn(emptyOrderDto(orderId, "OUT_FOR_DELIVERY"));

    mockMvc
        .perform(
            post("/api/v1/orders/{id}/ship", orderId)
                .with(jwt().jwt(jwt -> jwt.subject(UUID.randomUUID().toString()).claim("subject_type", "ADMIN")))
                .with(csrf()))
        .andExpect(status().isOk());
  }

  @Test
  void completeRequiresAdminJwtAndCsrf() throws Exception {
    UUID orderId = UUID.randomUUID();
    when(completeOrderUseCase.execute(any())).thenReturn(emptyOrderDto(orderId, "DELIVERED"));

    mockMvc
        .perform(
            post("/api/v1/orders/{id}/complete", orderId)
                .with(jwt().jwt(jwt -> jwt.subject(UUID.randomUUID().toString()).claim("subject_type", "ADMIN")))
                .with(csrf()))
        .andExpect(status().isOk());
  }

  @Test
  void rejectRequiresAdminJwtAndCsrf() throws Exception {
    UUID orderId = UUID.randomUUID();
    when(rejectOrderUseCase.execute(any())).thenReturn(emptyOrderDto(orderId, "REJECTED"));

    mockMvc
        .perform(
            post("/api/v1/orders/{id}/reject", orderId)
                .with(jwt().jwt(jwt -> jwt.subject(UUID.randomUUID().toString()).claim("subject_type", "ADMIN")))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RejectOrderRequest("Out of stock"))))
        .andExpect(status().isOk());
  }

  @Test
  void rejectWhenTheGatewayFailsReturns502WithPaymentGatewayErrorCode() throws Exception {
    UUID orderId = UUID.randomUUID();
    when(rejectOrderUseCase.execute(any()))
        .thenThrow(new PaymentGatewayException("The payment gateway could not process the refund"));

    mockMvc
        .perform(
            post("/api/v1/orders/{id}/reject", orderId)
                .with(jwt().jwt(jwt -> jwt.subject(UUID.randomUUID().toString()).claim("subject_type", "ADMIN")))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RejectOrderRequest("Out of stock"))))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.code").value("PAYMENT_GATEWAY_ERROR"));
  }

  @Test
  void rejectWithBlankReasonReturnsUnprocessable() throws Exception {
    UUID orderId = UUID.randomUUID();

    mockMvc
        .perform(
            post("/api/v1/orders/{id}/reject", orderId)
                .with(jwt().jwt(jwt -> jwt.subject(UUID.randomUUID().toString()).claim("subject_type", "ADMIN")))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RejectOrderRequest("  "))))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void cancelAsAdminRequiresAdminJwtAndCsrf() throws Exception {
    UUID orderId = UUID.randomUUID();
    when(cancelOrderUseCase.execute(any())).thenReturn(emptyOrderDto(orderId, "CANCELLED"));

    mockMvc
        .perform(
            post("/api/v1/orders/{id}/cancel", orderId)
                .with(jwt().jwt(jwt -> jwt.subject(UUID.randomUUID().toString()).claim("subject_type", "ADMIN")))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CancelOrderRequest("Customer request"))))
        .andExpect(status().isOk());
  }

  @Test
  void cancelAsCustomerRequiresCustomerJwtAndCsrf() throws Exception {
    UUID orderId = UUID.randomUUID();
    when(cancelOrderUseCase.execute(any())).thenReturn(emptyOrderDto(orderId, "CANCELLED"));

    mockMvc
        .perform(
            post("/api/v1/orders/{id}/cancel", orderId)
                .with(jwt().jwt(jwt -> jwt.subject(UUID.randomUUID().toString()).claim("subject_type", "CUSTOMER")))
                .with(csrf()))
        .andExpect(status().isOk());
  }

  @Test
  void cancelRequiresAuthentication() throws Exception {
    UUID orderId = UUID.randomUUID();

    mockMvc.perform(post("/api/v1/orders/{id}/cancel", orderId).with(csrf()))
        .andExpect(status().isUnauthorized());
  }

  private OrderDto emptyOrderDto(UUID id, String status) {
    return new OrderDto(
        id,
        "WEB-20260914-000001",
        "WEB",
        "PICKUP",
        status,
        List.of(),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        Instant.now());
  }
}
