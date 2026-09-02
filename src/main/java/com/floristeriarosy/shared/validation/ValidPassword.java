package com.floristeriarosy.shared.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Rejects a password shorter than the minimum length or found in a small denylist of common
 * passwords (00-security-validation-integrity.md, section 4). {@code null}/blank is left to
 * {@code @NotBlank}.
 */
@Documented
@Constraint(validatedBy = ValidPasswordValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPassword {

  String message() default "must be at least 10 characters and not a common password";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
