package com.floristeriarosy.application.admin.dto;

import com.floristeriarosy.domain.model.admin.AdminRole;
import java.time.Instant;
import java.util.UUID;

/**
 * @param id the identifier
 * @param email the decrypted email
 * @param role {@code OWNER} or {@code ADMIN}
 * @param active whether the admin can currently log in
 * @param totpEnabled whether TOTP is enrolled
 * @param passwordChangeRequired whether the current password is still provisional
 * @param createdAt when the row was created
 * @param updatedAt when the row was last updated
 */
public record AdminDto(
    UUID id,
    String email,
    AdminRole role,
    boolean active,
    boolean totpEnabled,
    boolean passwordChangeRequired,
    Instant createdAt,
    Instant updatedAt) {}
