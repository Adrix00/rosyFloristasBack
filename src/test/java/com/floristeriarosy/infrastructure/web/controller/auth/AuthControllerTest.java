package com.floristeriarosy.infrastructure.web.controller.auth;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.floristeriarosy.application.auth.dto.AdminLoginDto;
import com.floristeriarosy.application.auth.dto.AuthDto;
import com.floristeriarosy.application.auth.dto.TotpEnrollmentDto;
import com.floristeriarosy.application.auth.port.in.AdminLoginUseCase;
import com.floristeriarosy.application.auth.port.in.EnrollAdminTotpUseCase;
import com.floristeriarosy.application.auth.port.in.LogoutAllUseCase;
import com.floristeriarosy.application.auth.port.in.LogoutUseCase;
import com.floristeriarosy.application.auth.port.in.RefreshTokenUseCase;
import com.floristeriarosy.application.auth.port.in.VerifyAdminMfaUseCase;
import com.floristeriarosy.application.auth.port.out.AccessTokenPort;
import com.floristeriarosy.application.shared.port.out.PiiCryptoPort;
import com.floristeriarosy.domain.exception.auth.InvalidCredentialsException;
import com.floristeriarosy.domain.model.auth.SubjectType;
import com.floristeriarosy.infrastructure.security.config.SecurityConfig;
import com.floristeriarosy.infrastructure.web.mapper.auth.AuthWebMapper;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** {@link AuthController}: REST API for {@code /api/v1/auth} (auth.md, section 4). */
@WebMvcTest(AuthController.class)
@Import({AuthWebMapper.class, SecurityConfig.class})
class AuthControllerTest {

  @Autowired private MockMvc mockMvc;

  // SecurityConfig's real filter chain now needs RateLimitFilter, which needs these two.
  @MockitoBean private PiiCryptoPort piiCryptoPort;
  @MockitoBean private AccessTokenPort accessTokenPort;

  @MockitoBean private AdminLoginUseCase adminLoginUseCase;
  @MockitoBean private EnrollAdminTotpUseCase enrollAdminTotpUseCase;
  @MockitoBean private VerifyAdminMfaUseCase verifyAdminMfaUseCase;
  @MockitoBean private RefreshTokenUseCase refreshTokenUseCase;
  @MockitoBean private LogoutUseCase logoutUseCase;
  @MockitoBean private LogoutAllUseCase logoutAllUseCase;

  @BeforeEach
  void stubRateLimitFilterDependencies() {
    // RateLimitFilter (ADR-016) runs for real ahead of every /admin/login and /admin/mfa request
    // in this slice. A fresh key per call keeps every test's identifier bucket independent, since
    // the filter's bean (and its buckets) are shared across every test method in this class.
    when(piiCryptoPort.hmac(any()))
        .thenAnswer(invocation -> UUID.randomUUID().toString().getBytes());
    when(accessTokenPort.parse(any())).thenReturn(Optional.empty());
  }

  @Test
  void adminLoginWithoutCsrfIsForbidden() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/admin/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"owner@rosy.test\",\"password\":\"Correct!234\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminLoginReturns200WithTheMfaTokenNeverTheCredentials() throws Exception {
    when(adminLoginUseCase.execute(any()))
        .thenReturn(new AdminLoginDto("mfa-token", 300, true));

    mockMvc
        .perform(
            post("/api/v1/auth/admin/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"owner@rosy.test\",\"password\":\"Correct!234\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mfaToken").value("mfa-token"))
        .andExpect(jsonPath("$.enrollmentRequired").value(true))
        .andExpect(content().string(not(containsString("Correct!234"))));
  }

  @Test
  void adminLoginWithAnInvalidEmailReturns422() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/admin/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-an-email\",\"password\":\"Correct!234\"}"))
        .andExpect(status().is(422))
        .andExpect(jsonPath("$.code").value("AUTH_VALIDATION_FAILED"));
  }

  @Test
  void adminLoginWithWrongCredentialsReturns401() throws Exception {
    when(adminLoginUseCase.execute(any()))
        .thenThrow(new InvalidCredentialsException("Invalid email or password"));

    mockMvc
        .perform(
            post("/api/v1/auth/admin/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"owner@rosy.test\",\"password\":\"Wrong!234\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
  }

  @Test
  void totpEnrollmentReturns200WithTheSecretAndUri() throws Exception {
    when(enrollAdminTotpUseCase.execute(any()))
        .thenReturn(new TotpEnrollmentDto("otpauth://totp/x", "SECRETBASE32"));

    mockMvc
        .perform(
            post("/api/v1/auth/admin/totp/enrollment")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mfaToken\":\"mfa-token\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.secret").value("SECRETBASE32"))
        .andExpect(jsonPath("$.otpauthUri").value("otpauth://totp/x"));
  }

  @Test
  void verifyMfaWithACodeThatIsNotSixDigitsReturns422() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/admin/mfa")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mfaToken\":\"mfa-token\",\"code\":\"12\"}"))
        .andExpect(status().is(422))
        .andExpect(jsonPath("$.code").value("AUTH_VALIDATION_FAILED"));
  }

  @Test
  void verifyMfaReturns200WithAnAccessTokenAndTheRefreshCookieNeverTheRefreshTokenInTheBody()
      throws Exception {
    Instant expiresAt = Instant.now().plusSeconds(3600);
    when(verifyAdminMfaUseCase.execute(any()))
        .thenReturn(
            new AuthDto(
                "access-token", 300, SubjectType.ADMIN, "OWNER", false, "raw-refresh-token", expiresAt));

    mockMvc
        .perform(
            post("/api/v1/auth/admin/mfa")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mfaToken\":\"mfa-token\",\"code\":\"123456\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").value("access-token"))
        .andExpect(content().string(not(containsString("raw-refresh-token"))))
        .andExpect(cookie().value("refresh_token", "raw-refresh-token"))
        .andExpect(cookie().httpOnly("refresh_token", true))
        .andExpect(cookie().secure("refresh_token", true))
        .andExpect(cookie().path("refresh_token", "/api/v1/auth"))
        .andExpect(header().string("Set-Cookie", containsString("SameSite=Strict")));
  }

  @Test
  void refreshRotatesTheCookieAndNeverExposesEitherTokenValueInTheBody() throws Exception {
    Instant expiresAt = Instant.now().plusSeconds(3600);
    when(refreshTokenUseCase.execute(any()))
        .thenReturn(
            new AuthDto(
                "new-access-token", 300, SubjectType.ADMIN, "OWNER", false, "new-refresh-token", expiresAt));

    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .with(csrf())
                .cookie(new Cookie("refresh_token", "old-refresh-token")))
        .andExpect(status().isOk())
        .andExpect(cookie().value("refresh_token", "new-refresh-token"))
        .andExpect(content().string(not(containsString("new-refresh-token"))));
  }

  @Test
  void logoutWithoutACookieReturns204AndDoesNotCallTheUseCase() throws Exception {
    mockMvc
        .perform(post("/api/v1/auth/logout").with(csrf()))
        .andExpect(status().isNoContent())
        .andExpect(cookie().maxAge("refresh_token", 0));

    verify(logoutUseCase).execute(any());
  }

  @Test
  void logoutWithoutCsrfIsForbidden() throws Exception {
    mockMvc.perform(post("/api/v1/auth/logout")).andExpect(status().isForbidden());
  }

  @Test
  void logoutAllClearsTheCookieToo() throws Exception {
    mockMvc
        .perform(post("/api/v1/auth/logout-all").with(csrf()))
        .andExpect(status().isNoContent())
        .andExpect(cookie().maxAge("refresh_token", 0));
  }
}
