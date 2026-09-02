package com.floristeriarosy.application.inventory.command;

import java.util.UUID;

/**
 * @param id the alert being closed as acknowledged
 * @param note optional closing note
 */
public record DismissInventoryAlertCommand(UUID id, String note) {}
