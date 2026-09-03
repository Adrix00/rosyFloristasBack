package com.floristeriarosy.infrastructure.web.controller.auth;

import com.floristeriarosy.application.auth.dto.AuthDto;
import com.floristeriarosy.application.auth.port.in.AdminLoginUseCase;
import com.floristeriarosy.application.auth.port.in.EnrollAdminTotpUseCase;
import com.floristeriarosy.application.auth.port.in.LogoutAllUseCase;
import com.floristeriarosy.application.auth.port.in.LogoutUseCase;
import com.floristeriarosy.application.auth.port.in.RefreshTokenUseCase;
import com.floristeriarosy.application.auth.port.in.VerifyAdminMfaUseCase;
import com.floristeriarosy.infrastructure.web.mapper.auth.AuthWebMapper;
import com.floristeriarosy.infrastructure.web.request.auth.AdminLoginRequest;
import com.floristeriarosy.infrastructure.web.request.auth.AdminMfaRequest;
import com.floristeriarosy.infrastructure.web.request.auth.TotpEnrollmentRequest;
import com.floristeriarosy.infrastructure.web.response.auth.AdminLoginResponse;
import com.floristeriarosy.infrastructure.web.response.auth.AuthResponse;
import com.floristeriarosy.infrastructure.web.response.auth.TotpEnrollmentResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for {@code /api/v1/auth} (auth.md, section 4). Every endpoint here is public — this is
 * the door, not something behind it.
 *
 * <p>Builds the refresh-token cookie itself: that is HTTP transport, not business logic. The
 * service returns the plaintext token and its expiry in its DTO and never logs either.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private static final String REFRESH_TOKEN_COOKIE = "refresh_token";
  private static final String COOKIE_PATH = "/api/v1/auth";

  private static final Logger LOGGER = LoggerFactory.getLogger(AuthController.class);

  private final AdminLoginUseCase adminLoginUseCase;
  private final EnrollAdminTotpUseCase enrollAdminTotpUseCase;
  private final VerifyAdminMfaUseCase verifyAdminMfaUseCase;
  private final RefreshTokenUseCase refreshTokenUseCase;
  private final LogoutUseCase logoutUseCase;
  private final LogoutAllUseCase logoutAllUseCase;
  private final AuthWebMapper mapper;

  /**
   * @param adminLoginUseCase backs {@code POST /auth/admin/login}
   * @param enrollAdminTotpUseCase backs {@code POST /auth/admin/totp/enrollment}
   * @param verifyAdminMfaUseCase backs {@code POST /auth/admin/mfa}
   * @param refreshTokenUseCase backs {@code POST /auth/refresh}
   * @param logoutUseCase backs {@code POST /auth/logout}
   * @param logoutAllUseCase backs {@code POST /auth/logout-all}
   * @param mapper translates Request/Response to/from Command/Dto; the only class in this
   *     controller's call graph allowed to touch a domain type
   */
  public AuthController(
      AdminLoginUseCase adminLoginUseCase,
      EnrollAdminTotpUseCase enrollAdminTotpUseCase,
      VerifyAdminMfaUseCase verifyAdminMfaUseCase,
      RefreshTokenUseCase refreshTokenUseCase,
      LogoutUseCase logoutUseCase,
      LogoutAllUseCase logoutAllUseCase,
      AuthWebMapper mapper) {
    this.adminLoginUseCase = adminLoginUseCase;
    this.enrollAdminTotpUseCase = enrollAdminTotpUseCase;
    this.verifyAdminMfaUseCase = verifyAdminMfaUseCase;
    this.refreshTokenUseCase = refreshTokenUseCase;
    this.logoutUseCase = logoutUseCase;
    this.logoutAllUseCase = logoutAllUseCase;
    this.mapper = mapper;
  }

  /**
   * {@code POST /auth/admin/login}: step 1 of the admin login (auth.md, rule 3.3). Never logs the
   * email or password.
   *
   * @param request email and password
   * @return 200 with the ephemeral {@code mfaToken}
   */
  @PostMapping("/admin/login")
  public ResponseEntity<AdminLoginResponse> adminLogin(
      @Valid @RequestBody AdminLoginRequest request) {
    LOGGER.debug("POST /auth/admin/login");
    AdminLoginResponse response =
        mapper.toResponse(adminLoginUseCase.execute(mapper.toCommand(request)));
    LOGGER.debug(
        "POST /auth/admin/login -> 200 enrollmentRequired={}", response.enrollmentRequired());
    return ResponseEntity.ok(response);
  }

  /**
   * {@code POST /auth/admin/totp/enrollment}: generates a new TOTP secret (auth.md, rule 3.4).
   * Never logs the {@code mfaToken} or the returned secret.
   *
   * @param request the {@code mfaToken}
   * @return 200 with the enrollment URI and secret
   */
  @PostMapping("/admin/totp/enrollment")
  public ResponseEntity<TotpEnrollmentResponse> enrollTotp(
      @Valid @RequestBody TotpEnrollmentRequest request) {
    LOGGER.debug("POST /auth/admin/totp/enrollment");
    TotpEnrollmentResponse response =
        mapper.toResponse(enrollAdminTotpUseCase.execute(mapper.toCommand(request)));
    LOGGER.debug("POST /auth/admin/totp/enrollment -> 200");
    return ResponseEntity.ok(response);
  }

  /**
   * {@code POST /auth/admin/mfa}: step 2 of the admin login (auth.md, rule 3.3). Never logs the
   * {@code mfaToken}, the code, or the issued tokens.
   *
   * @param request the {@code mfaToken} and the 6-digit code
   * @return 200 with the access token, plus the refresh-token cookie
   */
  @PostMapping("/admin/mfa")
  public ResponseEntity<AuthResponse> verifyMfa(@Valid @RequestBody AdminMfaRequest request) {
    LOGGER.debug("POST /auth/admin/mfa");
    AuthDto dto = verifyAdminMfaUseCase.execute(mapper.toCommand(request));
    LOGGER.debug("POST /auth/admin/mfa -> 200");
    return ResponseEntity.ok()
        .header(
            HttpHeaders.SET_COOKIE,
            refreshCookie(dto.refreshToken(), dto.refreshTokenExpiresAt()).toString())
        .body(mapper.toResponse(dto));
  }

  /**
   * {@code POST /auth/refresh}: single-use rotation (auth.md, rule 3.5; ADR-008). A missing cookie
   * is treated the same as an unknown token. Never logs the presented or issued tokens.
   *
   * @param refreshToken the plaintext refresh token from the cookie, or blank if absent
   * @return 200 with the new access token, plus the rotated refresh-token cookie
   */
  @PostMapping("/refresh")
  public ResponseEntity<AuthResponse> refresh(
      @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false, defaultValue = "")
          String refreshToken) {
    LOGGER.debug("POST /auth/refresh");
    AuthDto dto = refreshTokenUseCase.execute(mapper.toRefreshCommand(refreshToken));
    LOGGER.debug("POST /auth/refresh -> 200");
    return ResponseEntity.ok()
        .header(
            HttpHeaders.SET_COOKIE,
            refreshCookie(dto.refreshToken(), dto.refreshTokenExpiresAt()).toString())
        .body(mapper.toResponse(dto));
  }

  /**
   * {@code POST /auth/logout}: revokes the family of this device (auth.md, rule 3.7). A missing
   * cookie is a no-op, not an error. Never logs the presented token.
   *
   * @param refreshToken the plaintext refresh token from the cookie, or {@code null} if absent
   * @return 204, with the cookie cleared
   */
  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshToken) {
    LOGGER.debug("POST /auth/logout cookiePresent={}", refreshToken != null);
    logoutUseCase.execute(mapper.toLogoutCommand(refreshToken));
    LOGGER.debug("POST /auth/logout -> 204");
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, clearedRefreshCookie().toString())
        .build();
  }

  /**
   * {@code POST /auth/logout-all}: revokes every family of this subject (auth.md, rule 3.7). A
   * missing cookie is a no-op, not an error. Never logs the presented token.
   *
   * @param refreshToken the plaintext refresh token from the cookie, or {@code null} if absent
   * @return 204, with the cookie cleared
   */
  @PostMapping("/logout-all")
  public ResponseEntity<Void> logoutAll(
      @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshToken) {
    LOGGER.debug("POST /auth/logout-all cookiePresent={}", refreshToken != null);
    logoutAllUseCase.execute(mapper.toLogoutAllCommand(refreshToken));
    LOGGER.debug("POST /auth/logout-all -> 204");
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, clearedRefreshCookie().toString())
        .build();
  }

  /**
   * @param refreshToken the plaintext refresh token to place in the cookie
   * @param expiresAt when the token's family absolutely expires, used as {@code Max-Age}
   * @return the {@code HttpOnly}, {@code Secure}, {@code SameSite=Strict} cookie (auth.md, rule
   *     3.1)
   */
  private ResponseCookie refreshCookie(String refreshToken, Instant expiresAt) {
    return ResponseCookie.from(REFRESH_TOKEN_COOKIE, refreshToken)
        .httpOnly(true)
        .secure(true)
        .sameSite("Strict")
        .path(COOKIE_PATH)
        .maxAge(Duration.between(Instant.now(), expiresAt))
        .build();
  }

  /**
   * @return the same cookie with an empty value and {@code Max-Age=0}, so the browser deletes it
   */
  private ResponseCookie clearedRefreshCookie() {
    return ResponseCookie.from(REFRESH_TOKEN_COOKIE, "")
        .httpOnly(true)
        .secure(true)
        .sameSite("Strict")
        .path(COOKIE_PATH)
        .maxAge(0)
        .build();
  }
}
