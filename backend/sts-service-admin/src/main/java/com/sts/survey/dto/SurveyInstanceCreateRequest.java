package com.sts.survey.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 创建一次问卷投放。
 */
@Data
public class SurveyInstanceCreateRequest {
  @NotBlank
  private String instanceCode;
  @NotBlank
  private String instanceName;
  @NotNull
  private Long surveyId;
  /**
   * 投放用途：TRAINING_SURVEY 线下培训调查，ORG_EVALUATION 培训机构评价。
   */
  @NotBlank
  private String purpose;
  /**
   * 结果可见方式：ADMIN_ONLY 仅管理员，SUBMITTER_AFTER_SUBMIT 提交后填写人可见。
   */
  @NotBlank
  private String resultVisibility;
  private LocalDateTime startTime;
  private LocalDateTime endTime;
}
