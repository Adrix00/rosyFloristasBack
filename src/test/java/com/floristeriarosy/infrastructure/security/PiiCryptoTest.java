package com.floristeriarosy.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** {@link PiiCrypto}: AES-256-GCM encryption and HMAC-SHA256 hashing (ADR-005). */
class PiiCryptoTest {

  private static final String ENCRYPTION_KEY_BASE64 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
  private static final String HMAC_PEPPER = "test-only-pepper";

  private final PiiCrypto adapter = new PiiCrypto(ENCRYPTION_KEY_BASE64, HMAC_PEPPER);

  @Test
  void decryptsExactlyWhatWasEncrypted() {
    byte[] ciphertext = adapter.encrypt("owner@rosy.test");

    assertThat(adapter.decrypt(ciphertext)).isEqualTo("owner@rosy.test");
  }

  @Test
  void encryptingTheSamePlaintextTwiceProducesDifferentCiphertext() {
    byte[] first = adapter.encrypt("owner@rosy.test");
    byte[] second = adapter.encrypt("owner@rosy.test");

    assertThat(first).isNotEqualTo(second);
  }

  @Test
  void hmacIsDeterministicForTheSameNormalizedInput() {
    byte[] first = adapter.hmac("owner@rosy.test");
    byte[] second = adapter.hmac("owner@rosy.test");

    assertThat(first).isEqualTo(second);
  }

  @Test
  void hmacDiffersForDifferentInputs() {
    byte[] first = adapter.hmac("owner@rosy.test");
    byte[] second = adapter.hmac("other@rosy.test");

    assertThat(first).isNotEqualTo(second);
  }

  @Test
  void failsToDecryptACorruptedCiphertext() {
    byte[] ciphertext = adapter.encrypt("owner@rosy.test");
    ciphertext[ciphertext.length - 1] ^= 0x01;

    assertThatThrownBy(() -> adapter.decrypt(ciphertext)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void rejectsPlaintextLongerThanTheMaximumLength() {
    String tooLong = "a".repeat(4097);

    assertThatThrownBy(() -> adapter.encrypt(tooLong)).isInstanceOf(IllegalArgumentException.class);
  }
}
