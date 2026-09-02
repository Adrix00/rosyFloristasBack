package com.floristeriarosy.application.admin.command;

import java.util.UUID;

/**
 * @param adminId the admin changing their own password
 * @param currentPassword the admin's current password, for verification
 * @param newPassword the new password
 */
public record ChangeOwnPasswordCommand(UUID adminId, String currentPassword, String newPassword) {}
