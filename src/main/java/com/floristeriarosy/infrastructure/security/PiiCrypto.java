package com.floristeriarosy.infrastructure.security;

import com.floristeriarosy.application.shared.port.out.PiiCryptoPort;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Implements {@link PiiCryptoPort} with AES-256-GCM (encryption) and HmacSHA256 (hashing),
 * ADR-005. The IV is random per call and travels prepended to the ciphertext, so a single key can
 * encrypt many rows without ever reusing an IV.
 */
@Component
public class PiiCrypto implements PiiCryptoPort {

  private static final Logger LOGGER = LoggerFactory.getLogger(PiiCrypto.class);

  private static final String DEFAULT_ENCRYPTION_KEY =
      "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
  private static final String DEFAULT_HMAC_PEPPER = "dev-only-insecure-pepper-change-me";

  private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
  private static final String HMAC_ALGORITHM = "HmacSHA256";
  private static final int GCM_IV_LENGTH_BYTES = 12;
  private static final int GCM_TAG_LENGTH_BITS = 128;

  /**
   * No PII field this port encrypts (email, phone, name, address, card message) comes close to
   * this. Bounding it here closes a CodeQL-flagged CWE-190 (uncontrolled arithmetic overflow):
   * without it, {@code iv.length + ciphertext.length} is computed from an attacker-influenced
   * length with no upper bound.
   */
  private static final int MAX_PLAINTEXT_LENGTH = 4096;

  private final SecureRandom secureRandom = new SecureRandom();
  private final SecretKeySpec encryptionKey;
  private final SecretKeySpec hmacKey;

  /**
   * @param encryptionKeyBase64 {@code app.security.pii-encryption-key}, Base64 of exactly 32 bytes
   *     (AES-256)
   * @param hmacPepper {@code app.security.pii-hmac-pepper}, a raw UTF-8 secret used as HMAC key
   *     material
   */
  public PiiCrypto(
      @Value("${app.security.pii-encryption-key}") String encryptionKeyBase64,
      @Value("${app.security.pii-hmac-pepper}") String hmacPepper) {
    warnIfStillDefault(encryptionKeyBase64, hmacPepper);
    this.encryptionKey =
        new SecretKeySpec(Base64.getDecoder().decode(encryptionKeyBase64), "AES");
    this.hmacKey = new SecretKeySpec(hmacPepper.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
  }

  /**
   * @param plaintext the value to encrypt
   * @return {@code IV || ciphertext || GCM tag}
   * @throws IllegalArgumentException {@code plaintext} is longer than {@link
   *     #MAX_PLAINTEXT_LENGTH}
   */
  @Override
  public byte[] encrypt(String plaintext) {
    if (plaintext.length() > MAX_PLAINTEXT_LENGTH) {
      throw new IllegalArgumentException(
          "Plaintext exceeds the maximum length this port encrypts (" + MAX_PLAINTEXT_LENGTH + " characters)");
    }
    try {
      byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
      secureRandom.nextBytes(iv);
      Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
      cipher.init(
          Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
      byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      // ByteArrayOutputStream grows its backing array on demand instead of the caller computing
      // iv.length + ciphertext.length itself — no arithmetic on ciphertext's length for CodeQL's
      // overflow check (CWE-190) to flag, and the bound above already caps the input regardless.
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      buffer.write(iv, 0, iv.length);
      buffer.write(ciphertext, 0, ciphertext.length);
      return buffer.toByteArray();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Failed to encrypt PII value", e);
    }
  }

  /**
   * @param ciphertext a value previously produced by {@link #encrypt(String)}
   * @return the original plaintext
   */
  @Override
  public String decrypt(byte[] ciphertext) {
    try {
      byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
      System.arraycopy(ciphertext, 0, iv, 0, GCM_IV_LENGTH_BYTES);
      Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
      cipher.init(
          Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
      byte[] plaintext =
          cipher.doFinal(
              ciphertext, GCM_IV_LENGTH_BYTES, ciphertext.length - GCM_IV_LENGTH_BYTES);
      return new String(plaintext, StandardCharsets.UTF_8);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Failed to decrypt PII value", e);
    }
  }

  /**
   * @param normalizedValue the already-normalized value to hash
   * @return the HMAC-SHA256 of {@code normalizedValue}
   */
  @Override
  public byte[] hmac(String normalizedValue) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(hmacKey);
      return mac.doFinal(normalizedValue.getBytes(StandardCharsets.UTF_8));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Failed to compute PII hash", e);
    }
  }

  /**
   * Logs once at startup if either secret still equals the insecure, checked-in dev default — the
   * one signal an operator has, from the logs, that {@code PII_ENCRYPTION_KEY}/{@code
   * PII_HMAC_PEPPER} were never set in this environment. Never logs the actual key/pepper values.
   *
   * @param encryptionKeyBase64 the configured encryption key, Base64-encoded
   * @param hmacPepper the configured HMAC pepper
   */
  private void warnIfStillDefault(String encryptionKeyBase64, String hmacPepper) {
    if (DEFAULT_ENCRYPTION_KEY.equals(encryptionKeyBase64) || DEFAULT_HMAC_PEPPER.equals(hmacPepper)) {
      LOGGER.warn(
          "PiiCrypto is running with a checked-in, insecure default key and/or pepper — "
              + "set PII_ENCRYPTION_KEY and PII_HMAC_PEPPER before this reaches production");
    }
  }
}
