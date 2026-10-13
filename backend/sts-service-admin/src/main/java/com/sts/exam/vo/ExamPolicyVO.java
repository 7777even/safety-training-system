package com.sts.exam.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 考试防作弊、补考与成绩可见性策略。
 *
 * <p>字段与 {@code ExamPolicyRequest} 一一对应，供编辑页回显；不做裁剪，
 * 否则会重新制造「写得进、读不全」的问题。
 */
@Data
public class ExamPolicyVO {
  private Long examPolicyId;
  private Long examId;
  /** 是否启用考试答题端切离页面事件审计：0 否、1 是。 */
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
  private LocalDateTime retakeStartTime;
  private LocalDateTime retakeEndTime;
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
