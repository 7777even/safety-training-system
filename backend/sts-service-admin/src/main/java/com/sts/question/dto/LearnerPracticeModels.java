package com.sts.question.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.*;

/** 个人练习的请求、冻结题目和裁剪后视图；身份始终由服务端读取。 */
public final class LearnerPracticeModels {
  private LearnerPracticeModels() { }
  @Data public static class Start {
    /** 分配练习来源，与错题来源二选一。 */
    private Long candidateId;
    /** 本人可查看的错题来源。 */
    private Long questionId;
    /** 练习或模拟模式。 */
    @NotBlank @Pattern(regexp = "PRACTICE|SIMULATION") private String mode;
    /** 重复请求复用会话的令牌。 */
    @NotBlank @Size(max = 100) private String clientToken;
  }
  @Data public static class Answer {
    /** 简答或兼容旧填空的答案。 */
    @Size(max = 10000) private String text;
    /** 选择的选项键。 */
    @NotNull @Size(max = 100) private Set<@NotBlank @Size(max = 100) String> selected = new LinkedHashSet<>();
    /** 逐空答案，键为blankNo。 */
    @NotNull @Size(max = 100) private Map<@Pattern(regexp = "[1-9][0-9]*") String, @NotNull @Size(max = 10000) String> blanks = new LinkedHashMap<>();
  }
  @Data public static class Query {
    /** 列表名称搜索。 */
    private String keyword;
  }
  /** 不携带选项正确标识的公开选项。 */
  public record Option(String key, String content) { }
  @Data public static class Question {
    /** 会话题目记录主键，错题列表使用原题目ID。 */
    private Long itemId;
    /** 原题目及版本引用。 */
    private Long questionId;
    private Long questionVersionId;
    /** 题型与冻结题干。 */
    private String type;
    private String stem;
    /** 安全选项与填空数量。 */
    private List<Option> options = List.of();
    private Integer blankCount;
    /** 按反馈权限裁剪的解析和标准答案。 */
    private String analysis;
    private String correctAnswerJson;
    /** 本人的作答、反馈、收藏。 */
    private Answer answer;
    private String evaluationResult;
    private boolean favorite;
    /** 错题来源及处理标识。 */
    private String sourceType;
    private String sourceName;
    private String wrongKey;
  }
  /** 会话统计仅有练习反馈，不存在正式分数/及格结论。 */
  public record View(Long sessionId, Long candidateId, Long sourceQuestionId, String title, String mode, String status,
                     LocalDateTime startedTime, LocalDateTime submittedTime,
                     int correctCount, int wrongCount, int unassessedCount,
                     Double accuracyRate, long usedSeconds, List<Question> questions,
                     /* 冻结题源及真实作答进度，列表不携带题目答案。 */
                     int totalCount, int answeredCount, String questionStem, String sourceType, String sourceName) { }
  /** 已分配练习的个人入口。 */
  public record Task(Long candidateId, String title, String kind) { }
  /** 收藏仅展示本人冻结题目摘要，不包含标准答案、解析或判定。 */
  public record FavoriteQuestion(Long questionId, Long questionVersionId, String type, String stem,
                                 String sourceType, String sourceName, Long sessionId, boolean available) { }
  /** 收藏和错题处理请求。 */
  public record Favorite(@NotNull Boolean favorite) { }
  public record Disposition(@NotBlank @Pattern(regexp = "DELETED|MASTERED") String disposition) { }
}
