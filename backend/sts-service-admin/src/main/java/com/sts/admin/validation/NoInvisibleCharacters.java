package com.sts.admin.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = NoInvisibleCharactersValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface NoInvisibleCharacters {

  String message() default "不得包含不可见或控制字符";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
