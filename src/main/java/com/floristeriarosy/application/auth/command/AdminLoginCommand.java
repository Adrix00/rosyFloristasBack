package com.floristeriarosy.application.auth.command;

/**
 * Step 1 of the admin login (auth.md, rule 3.3).
 *
 * @param email the raw email from the request, not yet normalized
 * @param password the raw plaintext password
 */
public record AdminLoginCommand(String email, String password) {}
