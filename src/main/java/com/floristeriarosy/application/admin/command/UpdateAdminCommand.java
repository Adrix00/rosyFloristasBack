package com.floristeriarosy.application.admin.command;

import com.floristeriarosy.domain.model.admin.AdminRole;
import java.util.UUID;

/**
 * @param actorId the {@code OWNER} performing the update, for the audit trail
 * @param id the admin to update
 * @param email the new email, not yet normalized
 * @param role the new role
 */
public record UpdateAdminCommand(UUID actorId, UUID id, String email, AdminRole role) {}
