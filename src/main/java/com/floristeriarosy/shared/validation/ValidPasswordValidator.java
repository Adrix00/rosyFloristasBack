package com.floristeriarosy.shared.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates {@link ValidPassword}: minimum length plus a small denylist of common passwords
 * (00-security-validation-integrity.md, section 4). Never logs the candidate password itself.
 */
public class ValidPasswordValidator implements ConstraintValidator<ValidPassword, String> {

  private static final Logger LOGGER = LoggerFactory.getLogger(ValidPasswordValidator.class);

  private static final int MIN_LENGTH = 10;

  private static final Set<String> COMMON_PASSWORDS =
      Set.of(
          "123456",
          "123456789",
          "12345678",
          "qwerty",
          "password",
          "password1",
          "111111",
          "123123",
          "abc123",
          "1234567890",
          "iloveyou",
          "admin",
          "admin123",
          "letmein",
          "welcome",
          "monkey",
          "dragon",
          "football",
          "qwertyuiop",
          "changeme");

  /**
   * @param value the candidate password; {@code null} is considered valid ({@code @NotBlank} owns
   *     that check)
   * @param context unused, required by the {@link ConstraintValidator} contract
   * @return {@code true} if {@code value} is {@code null}, or long enough and not a common
   *     password
   */
  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    boolean result =
        value == null
            || (value.length() >= MIN_LENGTH
                && !COMMON_PASSWORDS.contains(value.toLowerCase(Locale.ROOT)));
    LOGGER.debug("isValid -> {}", result);
    return result;
  }
}
