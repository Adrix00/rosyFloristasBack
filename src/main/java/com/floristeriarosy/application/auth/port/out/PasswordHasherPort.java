package com.floristeriarosy.application.auth.port.out;

/**
 * Hashes and verifies passwords (ADR-005: Argon2id, irreversible by design). Lives in {@code
 * auth.md} — admin.md and, later, customer.md both consume it for {@code password_hash}.
 */
public interface PasswordHasherPort {

  /**
   * @param rawPassword the plaintext password to hash
   * @return the Argon2id hash, ready to store in a {@code password_hash} column
   */
  String hash(String rawPassword);

  /**
   * @param rawPassword the plaintext password a caller supplied
   * @param hash a previously stored hash, produced by {@link #hash(String)}
   * @return whether {@code rawPassword} matches {@code hash}
   */
  boolean matches(String rawPassword, String hash);
}
