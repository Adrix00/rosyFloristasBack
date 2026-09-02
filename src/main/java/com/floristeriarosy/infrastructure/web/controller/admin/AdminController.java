package com.floristeriarosy.infrastructure.web.controller.admin;

import com.floristeriarosy.application.admin.port.in.ChangeAdminStatusUseCase;
import com.floristeriarosy.application.admin.port.in.ChangeOwnPasswordUseCase;
import com.floristeriarosy.application.admin.port.in.CreateAdminUseCase;
import com.floristeriarosy.application.admin.port.in.GetAdminUseCase;
import com.floristeriarosy.application.admin.port.in.GetAdminsUseCase;
import com.floristeriarosy.application.admin.port.in.ResetAdminPasswordUseCase;
import com.floristeriarosy.application.admin.port.in.ResetAdminTotpUseCase;
import com.floristeriarosy.application.admin.port.in.UpdateAdminUseCase;
import com.floristeriarosy.infrastructure.web.mapper.admin.AdminWebMapper;
import com.floristeriarosy.infrastructure.web.request.admin.ChangeAdminStatusRequest;
import com.floristeriarosy.infrastructure.web.request.admin.ChangeOwnPasswordRequest;
import com.floristeriarosy.infrastructure.web.request.admin.CreateAdminRequest;
import com.floristeriarosy.infrastructure.web.request.admin.UpdateAdminRequest;
import com.floristeriarosy.infrastructure.web.response.admin.AdminResponse;
import com.floristeriarosy.infrastructure.web.response.admin.PasswordResetResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for {@code /api/v1/admin} (admin.md, section 4).
 *
 * <p>Every management endpoint under {@code /admin/users} requires {@code OWNER} (rule 3.1);
 * unenforced today — no {@code @PreAuthorize} — since {@code feature/auth} does not exist yet and
 * {@code SecurityConfig} leaves every endpoint open (same tracked gap as {@code category.md},
 * dev-plan.md).
 *
 * <p>The two {@code /admin/me} endpoints, and the audit trail's actor id on every mutating
 * endpoint, resolve the caller from {@link Authentication#getName()} — the JWT subject once
 * {@code feature/auth}'s filter populates it (ADR-008). Until then, an anonymous request has no
 * numeric subject and calling any endpoint here fails; the wiring is shaped correctly for when
 * {@code feature/auth} lands, not a working substitute for it today.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

  private static final Logger LOGGER = LoggerFactory.getLogger(AdminController.class);

  private final CreateAdminUseCase createAdminUseCase;
  private final UpdateAdminUseCase updateAdminUseCase;
  private final ChangeAdminStatusUseCase changeAdminStatusUseCase;
  private final ResetAdminPasswordUseCase resetAdminPasswordUseCase;
  private final ResetAdminTotpUseCase resetAdminTotpUseCase;
  private final ChangeOwnPasswordUseCase changeOwnPasswordUseCase;
  private final GetAdminUseCase getAdminUseCase;
  private final GetAdminsUseCase getAdminsUseCase;
  private final AdminWebMapper mapper;

  /**
   * @param createAdminUseCase backs {@code POST /admin/users}
   * @param updateAdminUseCase backs {@code PUT /admin/users/{id}}
   * @param changeAdminStatusUseCase backs {@code PATCH /admin/users/{id}/status}
   * @param resetAdminPasswordUseCase backs {@code POST /admin/users/{id}/password-reset}
   * @param resetAdminTotpUseCase backs {@code POST /admin/users/{id}/totp-reset}
   * @param changeOwnPasswordUseCase backs {@code POST /admin/me/password}
   * @param getAdminUseCase backs {@code GET /admin/users/{id}} and {@code GET /admin/me}
   * @param getAdminsUseCase backs {@code GET /admin/users}
   * @param mapper translates Request/Response to/from Command/Query/Dto; the only class in this
   *     controller's call graph allowed to touch a domain type
   */
  public AdminController(
      CreateAdminUseCase createAdminUseCase,
      UpdateAdminUseCase updateAdminUseCase,
      ChangeAdminStatusUseCase changeAdminStatusUseCase,
      ResetAdminPasswordUseCase resetAdminPasswordUseCase,
      ResetAdminTotpUseCase resetAdminTotpUseCase,
      ChangeOwnPasswordUseCase changeOwnPasswordUseCase,
      GetAdminUseCase getAdminUseCase,
      GetAdminsUseCase getAdminsUseCase,
      AdminWebMapper mapper) {
    this.createAdminUseCase = createAdminUseCase;
    this.updateAdminUseCase = updateAdminUseCase;
    this.changeAdminStatusUseCase = changeAdminStatusUseCase;
    this.resetAdminPasswordUseCase = resetAdminPasswordUseCase;
    this.resetAdminTotpUseCase = resetAdminTotpUseCase;
    this.changeOwnPasswordUseCase = changeOwnPasswordUseCase;
    this.getAdminUseCase = getAdminUseCase;
    this.getAdminsUseCase = getAdminsUseCase;
    this.mapper = mapper;
  }

  /**
   * {@code GET /admin/users} ({@code OWNER}): filters by {@code active} and {@code role}.
   *
   * @param active optional status filter
   * @param role optional role filter, {@code OWNER} or {@code ADMIN}
   * @return 200 with the matching admins
   */
  @GetMapping("/users")
  public ResponseEntity<List<AdminResponse>> getAll(
      @RequestParam(required = false) Boolean active,
      @RequestParam(required = false) String role) {
    LOGGER.debug(
        "GET /admin/users active={} role={}", active, role == null ? null : Encode.forJava(role));
    List<AdminResponse> response =
        getAdminsUseCase.execute(mapper.toQuery(active, role)).stream()
            .map(mapper::toResponse)
            .toList();
    LOGGER.debug("GET /admin/users -> 200 count={}", response.size());
    return ResponseEntity.ok(response);
  }

  /**
   * {@code GET /admin/users/{id}} ({@code OWNER}).
   *
   * @param id the admin to look up
   * @return 200 with the matching admin
   */
  @GetMapping("/users/{id}")
  public ResponseEntity<AdminResponse> getOne(@PathVariable UUID id) {
    LOGGER.debug("GET /admin/users/{}", id);
    AdminResponse response = mapper.toResponse(getAdminUseCase.execute(mapper.toQuery(id)));
    LOGGER.debug("GET /admin/users/{} -> 200", id);
    return ResponseEntity.ok(response);
  }

  /**
   * {@code POST /admin/users} ({@code OWNER}): alta with a provisional password (admin.md, rule
   * 3.2).
   *
   * @param request email, provisional password and role of the admin to create
   * @param authentication the calling {@code OWNER}, for the audit trail
   * @return 201 with the created admin
   */
  @PostMapping("/users")
  public ResponseEntity<AdminResponse> create(
      @Valid @RequestBody CreateAdminRequest request, Authentication authentication) {
    LOGGER.debug("POST /admin/users role={}", request.role());
    AdminResponse response =
        mapper.toResponse(
            createAdminUseCase.execute(mapper.toCommand(resolveActorId(authentication), request)));
    LOGGER.debug("POST /admin/users -> 201 id={}", response.id());
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  /**
   * {@code PUT /admin/users/{id}} ({@code OWNER}): replaces email and role.
   *
   * @param id the admin to update
   * @param request the new field values
   * @param authentication the calling {@code OWNER}, for the audit trail
   * @return 200 with the updated admin
   */
  @PutMapping("/users/{id}")
  public ResponseEntity<AdminResponse> update(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateAdminRequest request,
      Authentication authentication) {
    LOGGER.debug("PUT /admin/users/{} role={}", id, request.role());
    AdminResponse response =
        mapper.toResponse(
            updateAdminUseCase.execute(
                mapper.toCommand(resolveActorId(authentication), id, request)));
    LOGGER.debug("PUT /admin/users/{} -> 200", id);
    return ResponseEntity.ok(response);
  }

  /**
   * {@code PATCH /admin/users/{id}/status} ({@code OWNER}): activates or deactivates.
   *
   * @param id the admin to change
   * @param request the new status
   * @param authentication the calling {@code OWNER}, for the audit trail
   * @return 200 with the updated admin
   */
  @PatchMapping("/users/{id}/status")
  public ResponseEntity<AdminResponse> changeStatus(
      @PathVariable UUID id,
      @Valid @RequestBody ChangeAdminStatusRequest request,
      Authentication authentication) {
    LOGGER.debug("PATCH /admin/users/{}/status active={}", id, request.active());
    AdminResponse response =
        mapper.toResponse(
            changeAdminStatusUseCase.execute(
                mapper.toCommand(resolveActorId(authentication), id, request)));
    LOGGER.debug("PATCH /admin/users/{}/status -> 200", id);
    return ResponseEntity.ok(response);
  }

  /**
   * {@code POST /admin/users/{id}/password-reset} ({@code OWNER}): fixes a new provisional
   * password and returns it once (admin.md, section 6).
   *
   * @param id the admin whose password is reset
   * @param authentication the calling {@code OWNER}, for the audit trail
   * @return 200 with the generated provisional password
   */
  @PostMapping("/users/{id}/password-reset")
  public ResponseEntity<PasswordResetResponse> resetPassword(
      @PathVariable UUID id, Authentication authentication) {
    LOGGER.debug("POST /admin/users/{}/password-reset", id);
    PasswordResetResponse response =
        mapper.toResponse(
            resetAdminPasswordUseCase.execute(
                mapper.toResetPasswordCommand(resolveActorId(authentication), id)));
    LOGGER.debug("POST /admin/users/{}/password-reset -> 200", id);
    return ResponseEntity.ok(response);
  }

  /**
   * {@code POST /admin/users/{id}/totp-reset} ({@code OWNER}): resets TOTP enrollment.
   *
   * @param id the admin whose TOTP is reset
   * @param authentication the calling {@code OWNER}, for the audit trail
   * @return 204, empty body
   */
  @PostMapping("/users/{id}/totp-reset")
  public ResponseEntity<Void> resetTotp(@PathVariable UUID id, Authentication authentication) {
    LOGGER.debug("POST /admin/users/{}/totp-reset", id);
    resetAdminTotpUseCase.execute(mapper.toResetTotpCommand(resolveActorId(authentication), id));
    LOGGER.debug("POST /admin/users/{}/totp-reset -> 204", id);
    return ResponseEntity.noContent().build();
  }

  /**
   * {@code GET /admin/me} (any admin, on themselves).
   *
   * @param authentication the calling admin
   * @return 200 with the caller's own admin record
   */
  @GetMapping("/me")
  public ResponseEntity<AdminResponse> getMe(Authentication authentication) {
    UUID adminId = resolveActorId(authentication);
    LOGGER.debug("GET /admin/me id={}", adminId);
    AdminResponse response = mapper.toResponse(getAdminUseCase.execute(mapper.toQuery(adminId)));
    LOGGER.debug("GET /admin/me -> 200");
    return ResponseEntity.ok(response);
  }

  /**
   * {@code POST /admin/me/password} (any admin, on themselves): the only endpoint reachable while
   * {@code password_change_required = true} (admin.md, section 4).
   *
   * @param request current and new password
   * @param authentication the calling admin
   * @return 204, empty body
   */
  @PostMapping("/me/password")
  public ResponseEntity<Void> changeOwnPassword(
      @Valid @RequestBody ChangeOwnPasswordRequest request, Authentication authentication) {
    UUID adminId = resolveActorId(authentication);
    LOGGER.debug("POST /admin/me/password id={}", adminId);
    changeOwnPasswordUseCase.execute(mapper.toCommand(adminId, request));
    LOGGER.debug("POST /admin/me/password -> 204");
    return ResponseEntity.noContent().build();
  }

  /**
   * Resolves the calling admin's id from the Spring Security principal name (ADR-008: JWT
   * subject). See the class-level note — this has no populated value until {@code feature/auth}'s
   * filter exists.
   *
   * @param authentication the current request's authentication
   * @return the calling admin's id
   * @throws IllegalArgumentException the principal name is not a UUID (no authenticated caller
   *     today)
   */
  private UUID resolveActorId(Authentication authentication) {
    return UUID.fromString(authentication.getName());
  }
}
