package com.floristeriarosy.shared.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Collection;
import java.util.HashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Validates {@link NoDuplicates}: rejects a collection that contains the same element twice. */
public class NoDuplicatesValidator implements ConstraintValidator<NoDuplicates, Collection<?>> {

  private static final Logger LOGGER = LoggerFactory.getLogger(NoDuplicatesValidator.class);

  /**
   * @param value the collection to check; {@code null} is considered valid ({@code @NotNull}/
   *     {@code @NotEmpty} own that check)
   * @param context unused, required by the {@link ConstraintValidator} contract
   * @return {@code true} if {@code value} is {@code null} or has no duplicate elements
   */
  @Override
  public boolean isValid(Collection<?> value, ConstraintValidatorContext context) {
    boolean result = value == null || new HashSet<>(value).size() == value.size();
    LOGGER.debug("isValid size={} -> {}", value == null ? 0 : value.size(), result);
    return result;
  }
}
