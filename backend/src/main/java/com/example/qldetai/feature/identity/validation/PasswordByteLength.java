package com.example.qldetai.feature.identity.validation;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Target({FIELD, PARAMETER, RECORD_COMPONENT, ANNOTATION_TYPE})
@Retention(RUNTIME)
@Constraint(validatedBy = PasswordByteLengthValidator.class)
public @interface PasswordByteLength {
    String MESSAGE =
            "Mật khẩu tối đa 72 byte UTF-8; tiếng Việt có dấu và emoji chiếm nhiều byte hơn.";

    String message() default MESSAGE;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
