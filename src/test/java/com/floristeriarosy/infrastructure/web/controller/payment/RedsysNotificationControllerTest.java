package com.floristeriarosy.infrastructure.web.controller.payment;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.floristeriarosy.application.auth.port.out.AccessTokenPort;
import com.floristeriarosy.application.payment.port.in.ReceiveRedsysNotificationUseCase;
import com.floristeriarosy.application.shared.port.out.PiiCryptoPort;
import com.floristeriarosy.infrastructure.security.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** {@code POST /api/v1/payments/redsys/notification} — payment.md §4, ADR-017. */
@WebMvcTest(RedsysNotificationController.class)
@Import({SecurityConfig.class})
class RedsysNotificationControllerTest {

  @Autowired private MockMvc mockMvc;

  // SecurityConfig's filter chain needs these for RateLimitFilter.
  @MockitoBean private PiiCryptoPort piiCryptoPort;
  @MockitoBean private AccessTokenPort accessTokenPort;

  @MockitoBean private ReceiveRedsysNotificationUseCase receiveRedsysNotificationUseCase;

  @Test
  void notificationAcceptsRedsysParamsAndReturnsOk() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/payments/redsys/notification")
                .param("Ds_SignatureVersion", "HMAC_SHA256_V1")
                .param("Ds_MerchantParameters", "dummy-params")
                .param("Ds_Signature", "dummy-signature"))
        .andExpect(status().isOk())
        .andExpect(content().string("OK"));

    verify(receiveRedsysNotificationUseCase).execute("dummy-params", "dummy-signature");
  }
}
