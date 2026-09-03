package com.floristeriarosy.application.auth.command;

/**
 * Step 2 of the admin login (auth.md, rule 3.3): confirms the TOTP code and issues the session.
 *
 * @param mfaToken the ephemeral token returned by {@code POST /auth/admin/login}
 * @param code the 6-digit TOTP code
 */
public record VerifyAdminMfaCommand(String mfaToken, String code) {}
