package com.sts.exam.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ExamPolicyRequest {
  /** 是否启用考试答题端切离页面事件审计：0 否、1 是。 */
  private Integer isAnticheatEnabled = 0;
  private Integer isWatermarkEnabled = 0;
  private Integer isPcAllowed = 1;
  private Integer isAppAllowed = 1;
  private Integer isH5Allowed = 1;
  private Integer switchLimit;
  private Integer isSplitScreenForceSubmit = 0;
  private Integer isCopyRefreshDevtoolDisabled = 0;
  private Integer inactivitySeconds;
  private Integer inactivityCountdownSeconds;
  private Integer minimumAnswerSeconds;
  private Integer isRetakeEnabled = 0;
  private String retakeScope;
  private LocalDateTime retakeStartTime;
  private LocalDateTime retakeEndTime;
  private Integer retakeMaxCount = 0;
  private String retakeScoreMode = "HIGHEST";
  private Integer isRetakeRecordVisible = 1;
  private BigDecimal passPoint = BigDecimal.ZERO;
  private BigDecimal failPoint = BigDecimal.ZERO;
  private BigDecimal absentPoint = BigDecimal.ZERO;
  private Integer lateForbidSeconds = 0;
  private String answerDetailMode = "SUBMITTED";
  private Integer isAnalysisVisible = 1;
  private Integer isScoreVisible = 1;
  private Integer isRankingEnabled = 0;
}
