package com.sts.training.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * EXAM 发布快照 schemaVersion=2。字段顺序固定，供规范化 hash。
 */
@Data
@JsonInclude(JsonInclude.Include.ALWAYS)
@JsonPropertyOrder({
  "schemaVersion", "sourceExamId", "examCode", "examName", "paperVersionId", "assessmentMode",
  "durationSeconds", "passScore", "passRatio", "questionOrderMode", "multiSelectScoring",
  "credit", "examCategoryId", "coverFileId", "responsibleUserId", "isNoticeEnabled", "noticeContent",
  "requiresManualReview", "policy", "reviewers"
})
public class TrainingExamResourceSnapshot {
  private int schemaVersion = 2;
  private Long sourceExamId;
  private String examCode;
  private String examName;
  private Long paperVersionId;
  private String assessmentMode;
  private Integer durationSeconds;
  private BigDecimal passScore;
  private BigDecimal passRatio;
  private String questionOrderMode;
  private String multiSelectScoring;
  private BigDecimal credit;
  private Long examCategoryId;
  private Long coverFileId;
  private Long responsibleUserId;
  private Integer isNoticeEnabled;
  private String noticeContent;
  private Boolean requiresManualReview;
  private TrainingExamPolicySnapshot policy;
  private List<TrainingExamReviewerSnapshot> reviewers = new ArrayList<>();

  /**
   * 去掉 BigDecimal 尾零，避免 MySQL JSON 列把 60.00 读成 60 后校验 hash 失败。
   */
  public void normalizeNumericFields() {
    passScore = plain(passScore);
    passRatio = plain(passRatio);
    credit = plain(credit);
    if (policy != null) {
      policy.setPassPoint(plain(policy.getPassPoint()));
      policy.setFailPoint(plain(policy.getFailPoint()));
      policy.setAbsentPoint(plain(policy.getAbsentPoint()));
    }
  }

  private static BigDecimal plain(BigDecimal value) {
    return value == null ? null : new BigDecimal(value.stripTrailingZeros().toPlainString());
  }
}
