package com.floristeriarosy.infrastructure.web.request.admin;

import com.floristeriarosy.domain.model.admin.AdminRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param email required; normalized before it is encrypted and hashed
 * @param role required; {@code OWNER} or {@code ADMIN}
 */
public record UpdateAdminRequest(
    @NotBlank @Email @Size(max = 255) String email, @NotNull AdminRole role) {}
