package com.floristeriarosy.application.inventory.command;

import java.util.UUID;

/**
 * @param id the alert being closed as fixed
 * @param note optional closing note
 */
public record ResolveInventoryAlertCommand(UUID id, String note) {}
