package com.floristeriarosy.application.admin.command;

import java.util.UUID;

/**
 * @param actorId the {@code OWNER} performing the reset, for the audit trail
 * @param id the admin whose password is reset
 */
public record ResetAdminPasswordCommand(UUID actorId, UUID id) {}
