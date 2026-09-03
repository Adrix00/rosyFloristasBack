package com.floristeriarosy.infrastructure.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.floristeriarosy.application.auth.port.out.AccessTokenPort;
import com.floristeriarosy.domain.model.auth.AccessTokenClaims;
import com.floristeriarosy.domain.model.auth.SubjectType;
import com.floristeriarosy.domain.model.auth.TokenType;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end security posture (feature/auth, phase 15): {@code SecurityConfig}'s real JWT wiring
 * and {@code @PreAuthorize} on real services, over a real HTTP request. Every other test mocks the
 * use case; this one is the only place that proves an anonymous caller actually gets 401 and a
 * wrong-role caller actually gets 403, end to end.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class AdminAuthorizationIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private AccessTokenPort accessTokenPort;

  private String tokenFor(String role, boolean passwordChangeRequired) {
    AccessTokenClaims claims =
        new AccessTokenClaims(
            UUID.randomUUID(), TokenType.ACCESS, SubjectType.ADMIN, role, passwordChangeRequired);
    return accessTokenPort.issue(claims, Duration.ofMinutes(5));
  }

  @Test
  void anonymousCallerGets401OnAnAdminEndpoint() throws Exception {
    mockMvc.perform(get("/api/v1/admin/users")).andExpect(status().isUnauthorized());
  }

  @Test
  void adminRoleGets403OnAnOwnerOnlyEndpoint() throws Exception {
    mockMvc
        .perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + tokenFor("ADMIN", false)))
        .andExpect(status().isForbidden());
  }

  @Test
  void ownerRoleGets200OnAnOwnerOnlyEndpoint() throws Exception {
    mockMvc
        .perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + tokenFor("OWNER", false)))
        .andExpect(status().isOk());
  }

  @Test
  void anMfaTokenIsRejectedExactlyLikeAnAnonymousCaller() throws Exception {
    String mfaToken =
        accessTokenPort.issue(
            new AccessTokenClaims(UUID.randomUUID(), TokenType.MFA, null, null, false),
            Duration.ofMinutes(5));

    mockMvc
        .perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + mfaToken))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void aSessionWithPasswordChangeRequiredIsBlockedFromAnUnrelatedEndpoint() throws Exception {
    String token = tokenFor("ADMIN", true);

    mockMvc
        .perform(get("/api/v1/admin/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  void aSessionWithPasswordChangeRequiredCanStillReachThePasswordChangeEndpoint() throws Exception {
    String token = tokenFor("ADMIN", true);

    // No admin row exists for this token's random subject, so the use case itself 404s — the
    // point here is only that PasswordChangeRequiredFilter let the request through to it.
    mockMvc
        .perform(
            post("/api/v1/admin/me/password")
                .with(csrf())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"Old!234567\",\"newPassword\":\"New!234567\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void aSessionWithPasswordChangeRequiredCanStillLogOut() throws Exception {
    String token = tokenFor("ADMIN", true);

    mockMvc
        .perform(post("/api/v1/auth/logout").with(csrf()).header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());
  }
}
