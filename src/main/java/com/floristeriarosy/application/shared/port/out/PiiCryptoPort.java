package com.floristeriarosy.application.shared.port.out;

/**
 * Encrypts, decrypts and HMACs personal data (ADR-005). Shared across every module that stores a
 * PII field ({@code admin.md} first, {@code customer.md}/{@code order.md} later): one key
 * management strategy, not one per module.
 */
public interface PiiCryptoPort {

  /**
   * @param plaintext the value to encrypt, e.g. a normalized email
   * @return the ciphertext, ready to store in a {@code BYTEA} column
   */
  byte[] encrypt(String plaintext);

  /**
   * @param ciphertext a value previously produced by {@link #encrypt(String)}
   * @return the original plaintext
   */
  String decrypt(byte[] ciphertext);

  /**
   * @param normalizedValue the already-normalized value to hash (ADR-005: normalize before
   *     hashing, so two equivalent inputs produce the same hash)
   * @return the HMAC, ready to store in a {@code BYTEA} column with a {@code UNIQUE} constraint
   */
  byte[] hmac(String normalizedValue);
}
