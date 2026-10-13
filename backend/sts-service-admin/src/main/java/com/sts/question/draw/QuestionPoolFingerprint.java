package com.sts.question.draw;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/**
 * 冻结题池跨域共用的稳定 SHA-256 指纹算法。
 */
public final class QuestionPoolFingerprint {
  private QuestionPoolFingerprint() {
  }

  public record Item(long questionId, long questionVersionId, String questionType,
                     String difficulty, List<Long> knowledgePointIds, String evaluationMode) {
  }

  public static String of(List<Item> items) {
    StringBuilder canonical = new StringBuilder();
    items.stream().sorted(Comparator.comparing(Item::questionId))
      .forEach(item -> canonical.append(item.questionId()).append('|')
        .append(item.questionVersionId()).append('|').append(value(item.questionType())).append('|')
        .append(value(item.difficulty())).append('|').append(value(item.evaluationMode())).append('|')
        .append(item.knowledgePointIds() == null ? "" : item.knowledgePointIds().stream().sorted().toList())
        .append('\n'));
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                                        .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ex) {
      throw new IllegalStateException("question pool fingerprint failed", ex);
    }
  }

  private static String value(String value) {
    return value == null ? "" : value;
  }
}
