package com.sts.admin.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class NoInvisibleCharactersValidator
  implements ConstraintValidator<NoInvisibleCharacters, CharSequence> {

  private static final int ASCII_SPACE = 0x20;

  @Override
  public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
    if (value == null) {
      return true;
    }
    return value.codePoints().noneMatch(NoInvisibleCharactersValidator::isProhibited);
  }

  private static boolean isProhibited(int codePoint) {
    int type = Character.getType(codePoint);
    return type == Character.CONTROL
      || type == Character.FORMAT
      || type == Character.SURROGATE
      || type == Character.PRIVATE_USE
      || type == Character.UNASSIGNED
      || codePoint != ASCII_SPACE && Character.isSpaceChar(codePoint);
  }
}
