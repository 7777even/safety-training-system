package com.sts.admin.validation;

import com.sts.admin.system.dto.ResetPasswordRequest;
import com.sts.admin.system.dto.UserCreateRequest;
import com.sts.admin.system.dto.UserUpdateRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Pattern;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class NoInvisibleCharactersTest {

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void rejectsControlFormatPrivateUseAndAbnormalSeparators() {
    List<String> invalidValues = List.of(
      "user\u0000name",
      "user\u200Bname",
      "user\u200Cname",
      "user\u200Dname",
      "user\u2060name",
      "user\uFEFFname",
      "user\u202Ename",
      "user\u00A0name",
      "user\u2028name",
      "user\uE000name",
      "user" + new String(Character.toChars(0xE0001)) + "name");

    for (String value : invalidValues) {
      assertThat(validator.validate(new Payload(value)))
        .as("U+%04X should be rejected", firstInvalidCodePoint(value))
        .hasSize(1);
    }
  }

  @Test
  void acceptsNullAndVisibleTextIncludingEmojiCombiningMarksAndAsciiSpace() {
    assertThat(validator.validate(new Payload(null))).isEmpty();
    assertThat(validator.validate(new Payload("张 三 😀 e\u0301"))).isEmpty();
  }

  @Test
  void userWriteDtosApplyTheConstraintToEveryConfirmedStringField() {
    assertAnnotatedFields(UserCreateRequest.class,
                          Set.of("userCode", "userName", "phone", "password", "memberType", "email", "jobTitle"));
    assertAnnotatedFields(UserUpdateRequest.class,
                          Set.of("userName", "phone", "memberType", "email", "jobTitle"));
    assertAnnotatedFields(ResetPasswordRequest.class, Set.of("password"));
  }

  @Test
  void passwordFieldsRequireLowercaseSha256Digest() throws NoSuchFieldException {
    assertThat(UserCreateRequest.class.getDeclaredField("password").getAnnotation(Pattern.class).regexp())
      .isEqualTo("^[a-f0-9]{64}$");
    assertThat(ResetPasswordRequest.class.getDeclaredField("password").getAnnotation(Pattern.class).regexp())
      .isEqualTo("^[a-f0-9]{64}$");
  }

  private static void assertAnnotatedFields(Class<?> type, Set<String> expectedFields) {
    assertThat(List.of(type.getDeclaredFields()))
      .filteredOn(field -> field.isAnnotationPresent(NoInvisibleCharacters.class))
      .extracting(java.lang.reflect.Field::getName)
      .containsExactlyInAnyOrderElementsOf(expectedFields);
  }

  private static int firstInvalidCodePoint(String value) {
    return value.codePoints()
      .filter(codePoint -> codePoint != ' '
        && (Character.getType(codePoint) == Character.CONTROL
        || Character.getType(codePoint) == Character.FORMAT
        || Character.getType(codePoint) == Character.SURROGATE
        || Character.getType(codePoint) == Character.PRIVATE_USE
        || Character.getType(codePoint) == Character.UNASSIGNED
        || Character.isSpaceChar(codePoint)))
      .findFirst()
      .orElseThrow();
  }

  private record Payload(@NoInvisibleCharacters String value) {
  }
}
