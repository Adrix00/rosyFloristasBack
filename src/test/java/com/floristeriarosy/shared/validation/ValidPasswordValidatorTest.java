package com.floristeriarosy.shared.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** {@link ValidPasswordValidator}: minimum length plus a common-password denylist. */
class ValidPasswordValidatorTest {

  private final ValidPasswordValidator validator = new ValidPasswordValidator();

  @Test
  void rejectsAPasswordShorterThanTheMinimumLength() {
    assertThat(validator.isValid("Short1!", null)).isFalse();
  }

  @Test
  void rejectsACommonPasswordEvenIfLongEnough() {
    assertThat(validator.isValid("1234567890", null)).isFalse();
  }

  @Test
  void rejectsACommonPasswordRegardlessOfCase() {
    assertThat(validator.isValid("QWERTYUIOP", null)).isFalse();
  }

  @Test
  void acceptsAReasonablePassword() {
    assertThat(validator.isValid("Tr0ubador&Xyz", null)).isTrue();
  }

  @Test
  void treatsNullAsValidLeavingItToNotBlank() {
    assertThat(validator.isValid(null, null)).isTrue();
  }
}
