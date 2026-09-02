package com.floristeriarosy.application.admin.command;

import java.util.UUID;

/**
 * @param actorId the {@code OWNER} performing the change, for the audit trail
 * @param id the admin to activate or deactivate
 * @param active the new status
 */
public record ChangeAdminStatusCommand(UUID actorId, UUID id, boolean active) {}
