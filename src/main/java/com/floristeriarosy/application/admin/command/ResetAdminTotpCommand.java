package com.floristeriarosy.application.admin.command;

import java.util.UUID;

/**
 * @param actorId the {@code OWNER} performing the reset, for the audit trail
 * @param id the admin whose TOTP is reset
 */
public record ResetAdminTotpCommand(UUID actorId, UUID id) {}
