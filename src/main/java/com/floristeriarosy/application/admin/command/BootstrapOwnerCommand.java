package com.floristeriarosy.application.admin.command;

/**
 * @param email the first {@code OWNER}'s email, read from {@code BOOTSTRAP_OWNER_EMAIL}
 * @param password the first {@code OWNER}'s provisional password, read from {@code
 *     BOOTSTRAP_OWNER_PASSWORD}
 */
public record BootstrapOwnerCommand(String email, String password) {}
