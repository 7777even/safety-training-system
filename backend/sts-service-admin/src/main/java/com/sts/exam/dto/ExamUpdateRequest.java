package com.sts.exam.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ExamUpdateRequest {
  @NotBlank private String examName;
  private Long examCategoryId;
  private Long coverFileId;
  private Long paperVersionId;
  private Long questionBankId;
  private Long responsibleUserId;
  private LocalDateTime openStartTime;
  private LocalDateTime openEndTime;
  private Integer durationSeconds;
  private BigDecimal passRatio;
  private BigDecimal passScore;
  @NotNull private BigDecimal credit;
  private String questionOrderMode;
  private String multiSelectScoring;
  private Integer isNoticeEnabled;
  private String noticeContent;
  @NotNull private Integer status;
  @NotNull private Integer version;
  private ExamPolicyRequest policy;
  /** 题库练习抽题规则；仅 examType=BANK_PRACTICE 使用，为空表示未配置。 */
  @Valid private List<ExamDrawRuleRequest> drawRules;
}
