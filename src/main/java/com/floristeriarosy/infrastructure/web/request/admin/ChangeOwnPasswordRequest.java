package com.floristeriarosy.infrastructure.web.request.admin;

import com.floristeriarosy.shared.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;

/**
 * @param currentPassword required; verified against the admin's stored password
 * @param newPassword required; must differ from {@code currentPassword} (admin.md, section 5)
 */
public record ChangeOwnPasswordRequest(
    @NotBlank String currentPassword, @NotBlank @ValidPassword String newPassword) {}
