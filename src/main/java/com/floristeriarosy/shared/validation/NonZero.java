package com.floristeriarosy.shared.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Rejects an {@link Integer} equal to {@code 0}; {@code null} is left to {@code @NotNull}. */
@Documented
@Constraint(validatedBy = NonZeroValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface NonZero {

  String message() default "must not be zero";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
