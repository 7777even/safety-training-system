package com.sts.training.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 计划专属考试快照中的完整 ExamPolicy。字段顺序固定，供规范化 hash。
 */
@Data
@JsonInclude(JsonInclude.Include.ALWAYS)
@JsonPropertyOrder({
  "isAnticheatEnabled", "isWatermarkEnabled", "isPcAllowed", "isAppAllowed", "isH5Allowed", "switchLimit",
  "isSplitScreenForceSubmit", "isCopyRefreshDevtoolDisabled", "inactivitySeconds",
  "inactivityCountdownSeconds", "minimumAnswerSeconds", "isRetakeEnabled", "retakeScope",
  "retakeStartTime", "retakeEndTime", "retakeMaxCount", "retakeScoreMode", "isRetakeRecordVisible",
  "passPoint", "failPoint", "absentPoint", "lateForbidSeconds", "answerDetailMode",
  "isAnalysisVisible", "isScoreVisible", "isRankingEnabled"
})
public class TrainingExamPolicySnapshot {
  private Integer isAnticheatEnabled;
  private Integer isWatermarkEnabled;
  private Integer isPcAllowed;
  private Integer isAppAllowed;
  private Integer isH5Allowed;
  private Integer switchLimit;
  private Integer isSplitScreenForceSubmit;
  private Integer isCopyRefreshDevtoolDisabled;
  private Integer inactivitySeconds;
  private Integer inactivityCountdownSeconds;
  private Integer minimumAnswerSeconds;
  private Integer isRetakeEnabled;
  private String retakeScope;
  private String retakeStartTime;
  private String retakeEndTime;
  private Integer retakeMaxCount;
  private String retakeScoreMode;
  private Integer isRetakeRecordVisible;
  private BigDecimal passPoint;
  private BigDecimal failPoint;
  private BigDecimal absentPoint;
  private Integer lateForbidSeconds;
  private String answerDetailMode;
  private Integer isAnalysisVisible;
  private Integer isScoreVisible;
  private Integer isRankingEnabled;
}
