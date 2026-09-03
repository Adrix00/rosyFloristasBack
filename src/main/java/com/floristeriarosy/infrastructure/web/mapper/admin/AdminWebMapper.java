package com.floristeriarosy.infrastructure.web.mapper.admin;

import com.floristeriarosy.application.admin.command.ChangeAdminStatusCommand;
import com.floristeriarosy.application.admin.command.ChangeOwnPasswordCommand;
import com.floristeriarosy.application.admin.command.CreateAdminCommand;
import com.floristeriarosy.application.admin.command.ResetAdminPasswordCommand;
import com.floristeriarosy.application.admin.command.ResetAdminTotpCommand;
import com.floristeriarosy.application.admin.command.UpdateAdminCommand;
import com.floristeriarosy.application.admin.dto.AdminDto;
import com.floristeriarosy.application.admin.dto.PasswordResetResult;
import com.floristeriarosy.application.admin.query.GetAdminQuery;
import com.floristeriarosy.application.admin.query.GetAdminsQuery;
import com.floristeriarosy.domain.model.admin.AdminRole;
import com.floristeriarosy.infrastructure.web.request.admin.ChangeAdminStatusRequest;
import com.floristeriarosy.infrastructure.web.request.admin.ChangeOwnPasswordRequest;
import com.floristeriarosy.infrastructure.web.request.admin.CreateAdminRequest;
import com.floristeriarosy.infrastructure.web.request.admin.UpdateAdminRequest;
import com.floristeriarosy.infrastructure.web.response.admin.AdminResponse;
import com.floristeriarosy.infrastructure.web.response.admin.PasswordResetResponse;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Only class in the controller's call graph allowed to touch domain-typed fields ({@code
 * AdminRole}): keeps the Controller itself domain-free (HexagonalArchitectureTest). Pure 1:1 field
 * mapping, not logged (see CLAUDE.md, Logging) — every call is already visible in the Controller's
 * own entry/exit log.
 */
@Component
public class AdminWebMapper {

  /**
   * @param actorId the {@code OWNER} performing the creation
   * @param request the create request
   * @return the command to hand to {@code CreateAdminUseCase}
   */
  public CreateAdminCommand toCommand(UUID actorId, CreateAdminRequest request) {
    return new CreateAdminCommand(actorId, request.email(), request.password(), request.role());
  }

  /**
   * @param actorId the {@code OWNER} performing the update
   * @param id the admin to update, from the path
   * @param request the new field values
   * @return the command to hand to {@code UpdateAdminUseCase}
   */
  public UpdateAdminCommand toCommand(UUID actorId, UUID id, UpdateAdminRequest request) {
    return new UpdateAdminCommand(actorId, id, request.email(), request.role());
  }

  /**
   * @param actorId the {@code OWNER} performing the change
   * @param id the admin to change, from the path
   * @param request the new status
   * @return the command to hand to {@code ChangeAdminStatusUseCase}
   */
  public ChangeAdminStatusCommand toCommand(
      UUID actorId, UUID id, ChangeAdminStatusRequest request) {
    return new ChangeAdminStatusCommand(actorId, id, request.active());
  }

  /**
   * @param actorId the {@code OWNER} performing the reset
   * @param id the admin whose password is reset, from the path
   * @return the command to hand to {@code ResetAdminPasswordUseCase}
   */
  public ResetAdminPasswordCommand toResetPasswordCommand(UUID actorId, UUID id) {
    return new ResetAdminPasswordCommand(actorId, id);
  }

  /**
   * @param actorId the {@code OWNER} performing the reset
   * @param id the admin whose TOTP is reset, from the path
   * @return the command to hand to {@code ResetAdminTotpUseCase}
   */
  public ResetAdminTotpCommand toResetTotpCommand(UUID actorId, UUID id) {
    return new ResetAdminTotpCommand(actorId, id);
  }

  /**
   * @param adminId the admin changing their own password
   * @param request the current and new password
   * @return the command to hand to {@code ChangeOwnPasswordUseCase}
   */
  public ChangeOwnPasswordCommand toCommand(UUID adminId, ChangeOwnPasswordRequest request) {
    return new ChangeOwnPasswordCommand(adminId, request.currentPassword(), request.newPassword());
  }

  /**
   * @param id the admin to look up, from the path or the authenticated caller
   * @return the query to hand to {@code GetAdminUseCase}
   */
  public GetAdminQuery toQuery(UUID id) {
    return new GetAdminQuery(id);
  }

  /**
   * @param active {@code null} for no filter, otherwise only admins with this status
   * @param role {@code null}, blank or unparseable for no filter, otherwise only admins with this
   *     role
   * @return the query to hand to {@code GetAdminsUseCase}
   */
  public GetAdminsQuery toQuery(Boolean active, String role) {
    return new GetAdminsQuery(active, parseRole(role));
  }

  /**
   * @param dto the admin to expose
   * @return its API representation
   */
  public AdminResponse toResponse(AdminDto dto) {
    return new AdminResponse(
        dto.id(),
        dto.email(),
        dto.role(),
        dto.active(),
        dto.totpEnabled(),
        dto.passwordChangeRequired(),
        dto.createdAt(),
        dto.updatedAt());
  }

  /**
   * @param result the generated provisional password
   * @return its API representation
   */
  public PasswordResetResponse toResponse(PasswordResetResult result) {
    return new PasswordResetResponse(result.temporaryPassword());
  }

  /**
   * @param role the raw {@code role} query parameter
   * @return the parsed role, or {@code null} if blank or not one of {@code OWNER}/{@code ADMIN}
   */
  private AdminRole parseRole(String role) {
    if (role == null || role.isBlank()) {
      return null;
    }
    try {
      return AdminRole.valueOf(role.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException unparseable) {
      return null;
    }
  }
}
