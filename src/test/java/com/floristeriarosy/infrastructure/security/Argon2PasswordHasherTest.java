package com.floristeriarosy.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** {@link Argon2PasswordHasher}: Argon2id password hashing and verification (ADR-005). */
class Argon2PasswordHasherTest {

  private final Argon2PasswordHasher adapter = new Argon2PasswordHasher();

  @Test
  void matchesReturnsTrueForTheCorrectPassword() {
    String hash = adapter.hash("Provisional!234");

    assertThat(adapter.matches("Provisional!234", hash)).isTrue();
  }

  @Test
  void matchesReturnsFalseForAWrongPassword() {
    String hash = adapter.hash("Provisional!234");

    assertThat(adapter.matches("SomethingElse!234", hash)).isFalse();
  }

  @Test
  void hashingTheSamePasswordTwiceProducesDifferentHashes() {
    String first = adapter.hash("Provisional!234");
    String second = adapter.hash("Provisional!234");

    assertThat(first).isNotEqualTo(second);
  }
}
