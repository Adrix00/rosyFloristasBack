package com.floristeriarosy.application.admin.query;

import com.floristeriarosy.domain.model.admin.AdminRole;

/**
 * @param active {@code null} for no filter, otherwise only admins with this status
 * @param role {@code null} for no filter, otherwise only admins with this role
 */
public record GetAdminsQuery(Boolean active, AdminRole role) {}
