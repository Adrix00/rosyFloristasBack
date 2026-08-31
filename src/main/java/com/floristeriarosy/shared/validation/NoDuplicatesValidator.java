package com.floristeriarosy.shared.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Collection;
import java.util.HashSet;

public class NoDuplicatesValidator implements ConstraintValidator<NoDuplicates, Collection<?>> {

  @Override
  public boolean isValid(Collection<?> value, ConstraintValidatorContext context) {
    return value == null || new HashSet<>(value).size() == value.size();
  }
}
