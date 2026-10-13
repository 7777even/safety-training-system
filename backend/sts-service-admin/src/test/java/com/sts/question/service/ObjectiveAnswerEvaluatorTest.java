package com.sts.question.service;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

/** 通过公共判定入口验证正式作答与个人练习一致的反馈规则。 */
class ObjectiveAnswerEvaluatorTest {
  @Test void legacySingleBlankStandardAnswerAcceptsStructuredBlankInput() {
    assertThat(ObjectiveAnswerEvaluator.correct("FILL", "{\"standardAnswer\":\"安全\",\"blanks\":[]}",
      java.util.Set.of(), java.util.Map.of("1", "安全"), null)).isTrue();
  }
  @Test void incompleteMultipleChoiceIsWrong() {
    assertThat(ObjectiveAnswerEvaluator.correct("MULTIPLE", "{\"correctOptions\":[\"A\",\"B\"]}",
      Set.of("A"), Map.of(), null)).isFalse();
  }
  @Test void fillAcceptsAlternativeAndTrimsEachBlank() {
    assertThat(ObjectiveAnswerEvaluator.correct("FILL", "{\"blanks\":[{\"blankNo\":1,\"answers\":[\"安全\",\"安全生产\"]},{\"blankNo\":2,\"answers\":[\"责任\"]}]}",
      Set.of(), Map.of("1", " 安全生产 ", "2", "责任"), null)).isTrue();
  }
  @Test void wrongBlankAndEmptyChoiceAreWrong() {
    assertThat(ObjectiveAnswerEvaluator.correct("FILL", "{\"standardAnswer\":\"安全\"}", Set.of(), Map.of(), "危险")).isFalse();
    assertThat(ObjectiveAnswerEvaluator.correct("SINGLE", "{\"correctOptions\":[\"A\"]}", Set.of(), Map.of(), "")).isFalse();
  }
}
