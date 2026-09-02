package com.floristeriarosy.shared.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Validates {@link NonZero}: rejects an {@link Integer} value of exactly {@code 0}. */
public class NonZeroValidator implements ConstraintValidator<NonZero, Integer> {

  private static final Logger LOGGER = LoggerFactory.getLogger(NonZeroValidator.class);

  /**
   * @param value the value to check; {@code null} is considered valid ({@code @NotNull} owns that
   *     check)
   * @param context unused, required by the {@link ConstraintValidator} contract
   * @return {@code true} if {@code value} is {@code null} or not zero
   */
  @Override
  public boolean isValid(Integer value, ConstraintValidatorContext context) {
    boolean result = value == null || value != 0;
    LOGGER.debug("isValid value={} -> {}", value, result);
    return result;
  }
}
