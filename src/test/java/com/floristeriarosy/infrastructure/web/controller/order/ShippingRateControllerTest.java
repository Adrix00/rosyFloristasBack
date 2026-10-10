package com.floristeriarosy.infrastructure.web.controller.order;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.floristeriarosy.application.auth.port.out.AccessTokenPort;
import com.floristeriarosy.application.order.dto.ShippingBandDto;
import com.floristeriarosy.application.order.dto.ShippingBandsDto;
import com.floristeriarosy.application.order.port.in.CreateShippingRateUseCase;
import com.floristeriarosy.application.order.port.in.GetShippingRatesUseCase;
import com.floristeriarosy.application.order.port.in.UpdateShippingRateUseCase;
import com.floristeriarosy.application.shared.port.out.PiiCryptoPort;
import com.floristeriarosy.domain.exception.order.ShippingRateNotFoundException;
import com.floristeriarosy.domain.exception.order.ShippingRatesInvalidException;
import com.floristeriarosy.infrastructure.security.config.SecurityConfig;
import com.floristeriarosy.infrastructure.web.mapper.order.ShippingRateWebMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** {@code /shipping-rates} — order.md §4, §9, through the real {@code GlobalExceptionHandler}. */
@WebMvcTest(ShippingRateController.class)
@Import({SecurityConfig.class, ShippingRateWebMapper.class})
class ShippingRateControllerTest {

  private static final String BODY =
      "{\"minKm\":10,\"maxKm\":15,\"price\":12.00,\"freeFromAmount\":100.00,\"active\":true}";

  @Autowired private MockMvc mockMvc;

  // SecurityConfig's filter chain needs these for RateLimitFilter.
  @MockitoBean private PiiCryptoPort piiCryptoPort;
  @MockitoBean private AccessTokenPort accessTokenPort;

  @MockitoBean private GetShippingRatesUseCase getShippingRatesUseCase;
  @MockitoBean private CreateShippingRateUseCase createShippingRateUseCase;
  @MockitoBean private UpdateShippingRateUseCase updateShippingRateUseCase;

  @Test
  void listingReturnsEveryBandAndTheRadius() throws Exception {
    when(getShippingRatesUseCase.execute())
        .thenReturn(new ShippingBandsDto(List.of(dto(0, 5), dto(5, 10)), new BigDecimal("10")));

    mockMvc
        .perform(get("/api/v1/shipping-rates").with(admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rates.length()").value(2))
        .andExpect(jsonPath("$.rates[0].minKm").value(0))
        .andExpect(jsonPath("$.deliveryRadiusKm").value(10));
  }

  @Test
  void creatingReturns201WithTheBand() throws Exception {
    when(createShippingRateUseCase.execute(any())).thenReturn(dto(10, 15));

    json(post("/api/v1/shipping-rates"), BODY)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.maxKm").value(15))
        .andExpect(jsonPath("$.active").value(true));
  }

  @Test
  void updatingReturns200WithTheBand() throws Exception {
    when(updateShippingRateUseCase.execute(any())).thenReturn(dto(10, 15));

    json(put("/api/v1/shipping-rates/{id}", UUID.randomUUID()), BODY)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.price").value(12.0));
  }

  @Test
  void aTariffThatBreaksTheRulesIs422WithTheInvalidCode() throws Exception {
    when(createShippingRateUseCase.execute(any()))
        .thenThrow(new ShippingRatesInvalidException("There is a gap between 5 km and 7 km"));

    json(post("/api/v1/shipping-rates"), BODY)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("SHIPPING_RATES_INVALID"))
        .andExpect(jsonPath("$.detail").value("There is a gap between 5 km and 7 km"));
  }

  @Test
  void aBandThatDoesNotExistIs404WithTheNotFoundCode() throws Exception {
    when(updateShippingRateUseCase.execute(any()))
        .thenThrow(new ShippingRateNotFoundException("Shipping band not found"));

    json(put("/api/v1/shipping-rates/{id}", UUID.randomUUID()), BODY)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("SHIPPING_RATE_NOT_FOUND"));
  }

  @Test
  void aMissingFieldIsA422ValidationFailure() throws Exception {
    json(post("/api/v1/shipping-rates"), "{\"minKm\":0,\"maxKm\":5,\"active\":true}")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("ORDER_VALIDATION_FAILED"));
  }

  @Test
  void moreDigitsThanTheColumnsHoldIsA422ValidationFailure() throws Exception {
    json(post("/api/v1/shipping-rates"), "{\"minKm\":1000.5,\"maxKm\":2000,\"price\":5,\"active\":true}")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("ORDER_VALIDATION_FAILED"));
    json(put("/api/v1/shipping-rates/{id}", UUID.randomUUID()), "{\"minKm\":0,\"maxKm\":5,\"price\":5.123,\"active\":true}")
        .andExpect(status().isUnprocessableContent());
  }

  @Test
  void aNegativePriceOrAZeroFreeThresholdIsA422ValidationFailure() throws Exception {
    json(post("/api/v1/shipping-rates"), "{\"minKm\":0,\"maxKm\":5,\"price\":-1,\"active\":true}")
        .andExpect(status().isUnprocessableContent());
    json(post("/api/v1/shipping-rates"), "{\"minKm\":0,\"maxKm\":5,\"price\":5,\"freeFromAmount\":0,\"active\":true}")
        .andExpect(status().isUnprocessableContent());
  }

  @Test
  void writesWithoutTheCsrfTokenAreRefused() throws Exception {
    // Not authenticated: a request carrying a bearer token is exempt from CSRF by design (ADR-008),
    // so the token check is only visible on a request without one.
    mockMvc
        .perform(post("/api/v1/shipping-rates").contentType(MediaType.APPLICATION_JSON).content(BODY))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            put("/api/v1/shipping-rates/{id}", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
        .andExpect(status().isForbidden());
  }

  private ResultActions json(MockHttpServletRequestBuilder request, String body) throws Exception {
    return mockMvc.perform(request.with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
    return jwt().jwt(jwt -> jwt.subject(UUID.randomUUID().toString()).claim("subject_type", "ADMIN"));
  }

  private ShippingBandDto dto(int minKm, int maxKm) {
    return new ShippingBandDto(
        UUID.randomUUID(), new BigDecimal(minKm), new BigDecimal(maxKm), new BigDecimal("12.00"), null, true);
  }
}
