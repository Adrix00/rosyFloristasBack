package com.floristeriarosy.infrastructure.web.controller.order;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.floristeriarosy.application.auth.port.out.AccessTokenPort;
import com.floristeriarosy.application.order.dto.CheckoutRedirectDto;
import com.floristeriarosy.application.order.dto.CheckoutStatusDto;
import com.floristeriarosy.application.order.port.in.CheckoutUseCase;
import com.floristeriarosy.application.order.port.in.GetCheckoutStatusUseCase;
import com.floristeriarosy.application.shared.port.out.PiiCryptoPort;
import com.floristeriarosy.domain.exception.order.DeliveryAddressNotGeocodableException;
import com.floristeriarosy.domain.exception.order.DeliveryOutOfRangeException;
import com.floristeriarosy.domain.exception.order.GeocodingUnavailableException;
import com.floristeriarosy.domain.model.order.OrderFulfillment;
import com.floristeriarosy.domain.model.payment.PaymentMethod;
import com.floristeriarosy.infrastructure.security.config.SecurityConfig;
import com.floristeriarosy.infrastructure.web.mapper.order.CheckoutWebMapper;
import com.floristeriarosy.infrastructure.web.request.order.CheckoutRequest;
import com.floristeriarosy.infrastructure.web.request.order.PickupRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

/** {@code POST /checkout} / {@code GET /checkout/{key}/status} — order.md §4. */
@WebMvcTest(CheckoutController.class)
@Import({SecurityConfig.class, CheckoutWebMapper.class})
class CheckoutControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  // SecurityConfig's filter chain needs these for RateLimitFilter.
  @MockitoBean private PiiCryptoPort piiCryptoPort;
  @MockitoBean private AccessTokenPort accessTokenPort;

  @MockitoBean private CheckoutUseCase checkoutUseCase;
  @MockitoBean private GetCheckoutStatusUseCase getCheckoutStatusUseCase;

  @Test
  void checkoutRequiresCsrfAndReturnsAccepted() throws Exception {
    when(checkoutUseCase.execute(any()))
        .thenReturn(new CheckoutRedirectDto("https://sis-t.redsys.es/sis/realizarPago", Map.of("k", "v")));
    CheckoutRequest request =
        new CheckoutRequest(
            OrderFulfillment.PICKUP,
            PaymentMethod.CARD_ONLINE,
            "tok_visa",
            null,
            null,
            null,
            null,
            new PickupRequest(LocalDate.now().plusDays(1), LocalTime.of(10, 0), LocalTime.of(11, 0)));

    mockMvc
        .perform(
            post("/api/v1/checkout")
                .with(jwt().jwt(jwt -> jwt.subject(UUID.randomUUID().toString()).claim("subject_type", "CUSTOMER")))
                .with(csrf())
                .header("Idempotency-Key", "idem-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isAccepted());
  }

  @Test
  void anAddressNotRealIsA422WithTheNotGeocodableCodeForTheFrontend() throws Exception {
    when(checkoutUseCase.execute(any()))
        .thenThrow(new DeliveryAddressNotGeocodableException("Could not locate the delivery address"));

    performCheckout()
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("DELIVERY_ADDRESS_NOT_GEOCODABLE"));
  }

  @Test
  void anAddressOutsideTheDeliveryAreaIsA422WithTheRadiusSoTheFrontendCanOfferPickup() throws Exception {
    when(checkoutUseCase.execute(any()))
        .thenThrow(
            new DeliveryOutOfRangeException(
                "The delivery address is outside the delivery area", new BigDecimal("10.0")));

    performCheckout()
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("DELIVERY_OUT_OF_RANGE"))
        .andExpect(jsonPath("$.maxDistanceKm").value(10.0));
  }

  @Test
  void anAddressThatCouldNotBeCheckedIsA502WithAFixedMessageAndNothingFromGoogle() throws Exception {
    when(checkoutUseCase.execute(any())).thenThrow(new GeocodingUnavailableException());

    performCheckout()
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.code").value("DELIVERY_GEOCODING_UNAVAILABLE"))
        .andExpect(jsonPath("$.detail").value("The delivery address could not be checked right now"));
  }

  private ResultActions performCheckout() throws Exception {
    CheckoutRequest request =
        new CheckoutRequest(
            OrderFulfillment.PICKUP,
            PaymentMethod.CARD_ONLINE,
            "tok_visa",
            null,
            null,
            null,
            null,
            new PickupRequest(LocalDate.now().plusDays(1), LocalTime.of(10, 0), LocalTime.of(11, 0)));
    return mockMvc.perform(
        post("/api/v1/checkout")
            .with(jwt().jwt(jwt -> jwt.subject(UUID.randomUUID().toString()).claim("subject_type", "CUSTOMER")))
            .with(csrf())
            .header("Idempotency-Key", "idem-err")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));
  }

  @Test
  void checkoutWithoutCsrfIsRejected() throws Exception {
    CheckoutRequest request =
        new CheckoutRequest(
            OrderFulfillment.PICKUP,
            PaymentMethod.CARD_ONLINE,
            "tok_visa",
            null,
            null,
            null,
            null,
            new PickupRequest(LocalDate.now().plusDays(1), LocalTime.of(10, 0), LocalTime.of(11, 0)));

    mockMvc
        .perform(
            post("/api/v1/checkout")
                .header("Idempotency-Key", "idem-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
  }

  @Test
  void getStatusReturnsTheCurrentStatus() throws Exception {
    when(getCheckoutStatusUseCase.execute(any())).thenReturn(new CheckoutStatusDto("PENDING", null, null));

    mockMvc.perform(get("/api/v1/checkout/idem-1/status")).andExpect(status().isOk());
  }
}
