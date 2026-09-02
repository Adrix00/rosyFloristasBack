package com.floristeriarosy.infrastructure.web.request.admin;

import com.floristeriarosy.domain.model.admin.AdminRole;
import com.floristeriarosy.shared.validation.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param email required; normalized before it is encrypted and hashed
 * @param password required; the provisional password the {@code OWNER} types (admin.md, section
 *     5)
 * @param role required; {@code OWNER} or {@code ADMIN}
 */
public record CreateAdminRequest(
    @NotBlank @Email @Size(max = 255) String email,
    @NotBlank @ValidPassword String password,
    @NotNull AdminRole role) {}
