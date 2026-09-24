package io.github.edtechdevelopment.identity.presentation.account.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Target({TYPE, ANNOTATION_TYPE})
@Retention(RUNTIME)
@Constraint(validatedBy = AccountUpdateValidator.class)
public @interface ValidAccountUpdate {

    String message() default "At least one account field must be provided";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
