package com.floristeriarosy.application.auth.command;

/**
 * Generates a new TOTP secret for the admin identified by {@code mfaToken} (auth.md, rule 3.4).
 *
 * @param mfaToken the ephemeral token returned by {@code POST /auth/admin/login}
 */
public record EnrollAdminTotpCommand(String mfaToken) {}
