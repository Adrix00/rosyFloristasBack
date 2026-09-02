package com.floristeriarosy.infrastructure.web.request.inventory;

import jakarta.validation.constraints.Size;

/**
 * @param note optional context for whoever reviews the history later (inventory.md, section 5)
 */
public record DismissAlertRequest(@Size(max = 500) String note) {}
