package com.floristeriarosy.infrastructure.web.controller.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.floristeriarosy.application.admin.command.ChangeAdminStatusCommand;
import com.floristeriarosy.application.admin.command.ChangeOwnPasswordCommand;
import com.floristeriarosy.application.admin.command.CreateAdminCommand;
import com.floristeriarosy.application.admin.command.ResetAdminPasswordCommand;
import com.floristeriarosy.application.admin.command.UpdateAdminCommand;
import com.floristeriarosy.application.admin.dto.AdminDto;
import com.floristeriarosy.application.admin.dto.PasswordResetResult;
import com.floristeriarosy.application.admin.port.in.ChangeAdminStatusUseCase;
import com.floristeriarosy.application.admin.port.in.ChangeOwnPasswordUseCase;
import com.floristeriarosy.application.admin.port.in.CreateAdminUseCase;
import com.floristeriarosy.application.admin.port.in.GetAdminUseCase;
import com.floristeriarosy.application.admin.port.in.GetAdminsUseCase;
import com.floristeriarosy.application.admin.port.in.ResetAdminPasswordUseCase;
import com.floristeriarosy.application.admin.port.in.ResetAdminTotpUseCase;
import com.floristeriarosy.application.admin.port.in.UpdateAdminUseCase;
import com.floristeriarosy.application.admin.query.GetAdminQuery;
import com.floristeriarosy.domain.exception.admin.AdminNotFoundException;
import com.floristeriarosy.domain.exception.admin.InvalidCurrentPasswordException;
import com.floristeriarosy.domain.exception.admin.LastOwnerCannotBeRemovedException;
import com.floristeriarosy.domain.model.admin.AdminRole;
import com.floristeriarosy.infrastructure.security.config.SecurityConfig;
import com.floristeriarosy.infrastructure.web.mapper.admin.AdminWebMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** {@link AdminController}: REST API for {@code /api/v1/admin} (admin.md, section 4). */
@WebMvcTest(AdminController.class)
@Import({AdminWebMapper.class, SecurityConfig.class})
class AdminControllerTest {

  private static final UUID ACTOR_ID = UUID.randomUUID();

  @Autowired private MockMvc mockMvc;

  @MockitoBean private CreateAdminUseCase createAdminUseCase;
  @MockitoBean private UpdateAdminUseCase updateAdminUseCase;
  @MockitoBean private ChangeAdminStatusUseCase changeAdminStatusUseCase;
  @MockitoBean private ResetAdminPasswordUseCase resetAdminPasswordUseCase;
  @MockitoBean private ResetAdminTotpUseCase resetAdminTotpUseCase;
  @MockitoBean private ChangeOwnPasswordUseCase changeOwnPasswordUseCase;
  @MockitoBean private GetAdminUseCase getAdminUseCase;
  @MockitoBean private GetAdminsUseCase getAdminsUseCase;

  private AdminDto adminDto(UUID id, AdminRole role) {
    return new AdminDto(id, "admin@rosy.test", role, true, false, true, Instant.now(), Instant.now());
  }

  @Test
  void getAllReturns200WithTheMatchingAdmins() throws Exception {
    AdminDto dto = adminDto(UUID.randomUUID(), AdminRole.OWNER);
    when(getAdminsUseCase.execute(any())).thenReturn(List.of(dto));

    mockMvc
        .perform(get("/api/v1/admin/users"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].email").value("admin@rosy.test"))
        .andExpect(jsonPath("$[0].role").value("OWNER"));
  }

  @Test
  void getOneUnknownAdminReturns404WithAdminNotFoundCode() throws Exception {
    when(getAdminUseCase.execute(any(GetAdminQuery.class)))
        .thenThrow(new AdminNotFoundException("Admin not found"));

    mockMvc
        .perform(get("/api/v1/admin/users/" + UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ADMIN_NOT_FOUND"));
  }

  @Test
  void createReturns201WithTheCreatedAdmin() throws Exception {
    AdminDto dto = adminDto(UUID.randomUUID(), AdminRole.ADMIN);
    when(createAdminUseCase.execute(any(CreateAdminCommand.class))).thenReturn(dto);

    mockMvc
        .perform(
            post("/api/v1/admin/users")
                .with(csrf())
                .with(user(ACTOR_ID.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"new@rosy.test\",\"password\":\"Provisional!234\",\"role\":\"ADMIN\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.role").value("ADMIN"));
  }

  @Test
  void createWithAWeakPasswordReturns422WithAdminValidationCode() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/users")
                .with(csrf())
                .with(user(ACTOR_ID.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"new@rosy.test\",\"password\":\"short\",\"role\":\"ADMIN\"}"))
        .andExpect(status().is(422))
        .andExpect(jsonPath("$.code").value("ADMIN_VALIDATION_FAILED"));
  }

  @Test
  void createWithABlankEmailReturns422WithAdminValidationCode() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/users")
                .with(csrf())
                .with(user(ACTOR_ID.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"\",\"password\":\"Provisional!234\",\"role\":\"ADMIN\"}"))
        .andExpect(status().is(422))
        .andExpect(jsonPath("$.code").value("ADMIN_VALIDATION_FAILED"));
  }

  @Test
  void updateDemotingTheLastActiveOwnerReturns409() throws Exception {
    when(updateAdminUseCase.execute(any(UpdateAdminCommand.class)))
        .thenThrow(new LastOwnerCannotBeRemovedException("Cannot demote the last active OWNER"));

    mockMvc
        .perform(
            put("/api/v1/admin/users/" + UUID.randomUUID())
                .with(csrf())
                .with(user(ACTOR_ID.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"owner@rosy.test\",\"role\":\"ADMIN\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("LAST_OWNER_CANNOT_BE_REMOVED"));
  }

  @Test
  void changeStatusReturns200WithTheUpdatedAdmin() throws Exception {
    UUID id = UUID.randomUUID();
    AdminDto dto = adminDto(id, AdminRole.ADMIN);
    when(changeAdminStatusUseCase.execute(any(ChangeAdminStatusCommand.class))).thenReturn(dto);

    mockMvc
        .perform(
            patch("/api/v1/admin/users/" + id + "/status")
                .with(csrf())
                .with(user(ACTOR_ID.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id.toString()));
  }

  @Test
  void passwordResetReturns200WithTheGeneratedPasswordOnce() throws Exception {
    when(resetAdminPasswordUseCase.execute(any(ResetAdminPasswordCommand.class)))
        .thenReturn(new PasswordResetResult("generated-temporary-password"));

    mockMvc
        .perform(
            post("/api/v1/admin/users/" + UUID.randomUUID() + "/password-reset")
                .with(csrf())
                .with(user(ACTOR_ID.toString())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.temporaryPassword").value("generated-temporary-password"));
  }

  @Test
  void totpResetReturns204() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/users/" + UUID.randomUUID() + "/totp-reset")
                .with(csrf())
                .with(user(ACTOR_ID.toString())))
        .andExpect(status().isNoContent());
  }

  @Test
  void getMeReturns200WithTheCallersOwnRecord() throws Exception {
    AdminDto dto = adminDto(ACTOR_ID, AdminRole.OWNER);
    when(getAdminUseCase.execute(any(GetAdminQuery.class))).thenReturn(dto);

    mockMvc
        .perform(get("/api/v1/admin/me").with(user(ACTOR_ID.toString())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(ACTOR_ID.toString()));
  }

  @Test
  void changeOwnPasswordWithWrongCurrentPasswordReturns401() throws Exception {
    doThrow(new InvalidCurrentPasswordException("Current password does not match"))
        .when(changeOwnPasswordUseCase)
        .execute(any(ChangeOwnPasswordCommand.class));

    mockMvc
        .perform(
            post("/api/v1/admin/me/password")
                .with(csrf())
                .with(user(ACTOR_ID.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"wrong-current-pass\",\"newPassword\":\"NewPassword!234\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));
  }

  @Test
  void changeOwnPasswordSucceedsReturns204() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/me/password")
                .with(csrf())
                .with(user(ACTOR_ID.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"current-password-1\",\"newPassword\":\"NewPassword!234\"}"))
        .andExpect(status().isNoContent());
  }
}
