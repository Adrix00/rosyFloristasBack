package com.floristeriarosy.infrastructure.web.request.admin;

import jakarta.validation.constraints.NotNull;

/**
 * @param active required; the new status
 */
public record ChangeAdminStatusRequest(@NotNull Boolean active) {}
