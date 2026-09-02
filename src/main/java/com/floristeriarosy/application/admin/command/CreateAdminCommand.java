package com.floristeriarosy.application.admin.command;

import com.floristeriarosy.domain.model.admin.AdminRole;
import java.util.UUID;

/**
 * @param actorId the {@code OWNER} performing the creation, for the audit trail
 * @param email the new admin's email, not yet normalized
 * @param password the provisional password the {@code OWNER} typed
 * @param role {@code OWNER} or {@code ADMIN}
 */
public record CreateAdminCommand(UUID actorId, String email, String password, AdminRole role) {}
