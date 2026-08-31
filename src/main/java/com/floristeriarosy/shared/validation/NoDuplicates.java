package com.floristeriarosy.shared.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Rejects a {@link java.util.Collection} that contains the same element twice. */
@Documented
@Constraint(validatedBy = NoDuplicatesValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface NoDuplicates {

  String message() default "must not contain duplicate elements";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
