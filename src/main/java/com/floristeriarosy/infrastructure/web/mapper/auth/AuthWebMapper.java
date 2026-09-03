package com.floristeriarosy.infrastructure.web.mapper.auth;

import com.floristeriarosy.application.auth.command.AdminLoginCommand;
import com.floristeriarosy.application.auth.command.EnrollAdminTotpCommand;
import com.floristeriarosy.application.auth.command.LogoutAllCommand;
import com.floristeriarosy.application.auth.command.LogoutCommand;
import com.floristeriarosy.application.auth.command.RefreshTokenCommand;
import com.floristeriarosy.application.auth.command.VerifyAdminMfaCommand;
import com.floristeriarosy.application.auth.dto.AdminLoginDto;
import com.floristeriarosy.application.auth.dto.AuthDto;
import com.floristeriarosy.application.auth.dto.TotpEnrollmentDto;
import com.floristeriarosy.infrastructure.web.request.auth.AdminLoginRequest;
import com.floristeriarosy.infrastructure.web.request.auth.AdminMfaRequest;
import com.floristeriarosy.infrastructure.web.request.auth.TotpEnrollmentRequest;
import com.floristeriarosy.infrastructure.web.response.auth.AdminLoginResponse;
import com.floristeriarosy.infrastructure.web.response.auth.AuthResponse;
import com.floristeriarosy.infrastructure.web.response.auth.TotpEnrollmentResponse;
import org.springframework.stereotype.Component;

/**
 * Only class in the controller's call graph allowed to touch domain-typed fields ({@code
 * SubjectType}): keeps the Controller itself domain-free (HexagonalArchitectureTest). Pure 1:1
 * field mapping, not logged (see CLAUDE.md, Logging) — every call is already visible in the
 * Controller's own entry/exit log.
 */
@Component
public class AuthWebMapper {

  /**
   * @param request email and password
   * @return the command to hand to {@code AdminLoginUseCase}
   */
  public AdminLoginCommand toCommand(AdminLoginRequest request) {
    return new AdminLoginCommand(request.email(), request.password());
  }

  /**
   * @param request the {@code mfaToken}
   * @return the command to hand to {@code EnrollAdminTotpUseCase}
   */
  public EnrollAdminTotpCommand toCommand(TotpEnrollmentRequest request) {
    return new EnrollAdminTotpCommand(request.mfaToken());
  }

  /**
   * @param request the {@code mfaToken} and the 6-digit code
   * @return the command to hand to {@code VerifyAdminMfaUseCase}
   */
  public VerifyAdminMfaCommand toCommand(AdminMfaRequest request) {
    return new VerifyAdminMfaCommand(request.mfaToken(), request.code());
  }

  /**
   * @param refreshToken the plaintext refresh token from the cookie
   * @return the command to hand to {@code RefreshTokenUseCase}
   */
  public RefreshTokenCommand toRefreshCommand(String refreshToken) {
    return new RefreshTokenCommand(refreshToken);
  }

  /**
   * @param refreshToken the plaintext refresh token from the cookie, or {@code null} if absent
   * @return the command to hand to {@code LogoutUseCase}
   */
  public LogoutCommand toLogoutCommand(String refreshToken) {
    return new LogoutCommand(refreshToken);
  }

  /**
   * @param refreshToken the plaintext refresh token from the cookie, or {@code null} if absent
   * @return the command to hand to {@code LogoutAllUseCase}
   */
  public LogoutAllCommand toLogoutAllCommand(String refreshToken) {
    return new LogoutAllCommand(refreshToken);
  }

  /**
   * @param dto the ephemeral {@code mfaToken} and enrollment state
   * @return its API representation
   */
  public AdminLoginResponse toResponse(AdminLoginDto dto) {
    return new AdminLoginResponse(dto.mfaToken(), dto.expiresInSeconds(), dto.enrollmentRequired());
  }

  /**
   * @param dto the generated TOTP secret and enrollment URI
   * @return its API representation
   */
  public TotpEnrollmentResponse toResponse(TotpEnrollmentDto dto) {
    return new TotpEnrollmentResponse(dto.otpauthUri(), dto.secret());
  }

  /**
   * @param dto the issued session; its refresh token is never included here — the controller writes
   *     it to a cookie instead (auth.md, rule 3.1)
   * @return its API representation
   */
  public AuthResponse toResponse(AuthDto dto) {
    return new AuthResponse(
        dto.accessToken(),
        dto.expiresInSeconds(),
        dto.subjectType().name(),
        dto.role(),
        dto.passwordChangeRequired());
  }
}
