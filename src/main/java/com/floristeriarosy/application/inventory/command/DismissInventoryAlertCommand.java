package com.floristeriarosy.application.inventory.command;

import java.util.UUID;

/**
 * @param id the alert being closed as acknowledged
 * @param note optional closing note
 * @param adminUserId the admin who closed it, resolved from the caller's JWT
 */
public record DismissInventoryAlertCommand(UUID id, String note, UUID adminUserId) {}
