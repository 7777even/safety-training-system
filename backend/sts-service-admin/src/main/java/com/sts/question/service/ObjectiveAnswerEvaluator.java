package com.sts.question.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

/** 正式考试与个人练习共用的客观题对错判定，不生成正式成绩。 */
public final class ObjectiveAnswerEvaluator {
  private static final ObjectMapper JSON = new ObjectMapper();
  private ObjectiveAnswerEvaluator() { }

  /** 使用服务端冻结答案判定选项或逐空答案；调用方负责区分仅记录的主观题。 */
  public static boolean correct(String type, String snapshot, Set<String> selected,
                                Map<String, String> blanks, String text) {
    try {
      JsonNode root = JSON.readTree(snapshot == null ? "{}" : snapshot);
      if ("FILL".equals(type)) {
        JsonNode expected = root.path("blanks");
        if (expected.isEmpty()) {
          // 旧单空题只有standardAnswer，个人练习仍使用统一逐空输入。
          String given = blanks.getOrDefault("1", text == null ? "" : text);
          return !given.isBlank() && given.equals(root.path("standardAnswer").asText(""));
        }
        for (JsonNode blank : expected) {
          String given = blanks.getOrDefault(blank.path("blankNo").asText(), "").trim();
          boolean matched = false;
          for (JsonNode value : blank.path("answers")) if (value.asText().trim().equals(given)) matched = true;
          if (!matched) return false;
        }
        return true;
      }
      Set<String> keys = new HashSet<>();
      root.path("correctOptions").forEach(key -> keys.add(key.asText()));
      return !selected.isEmpty() && keys.equals(selected);
    } catch (Exception error) {
      return false;
    }
  }
}
