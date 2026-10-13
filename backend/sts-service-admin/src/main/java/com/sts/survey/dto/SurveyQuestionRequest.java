package com.sts.survey.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 最小闭环题目：单选、多选或文本。
 */
@Data
public class SurveyQuestionRequest {
  /**
   * 题型：SINGLE / MULTIPLE / TEXT。
   */
  @NotBlank
  private String questionType;
  @NotBlank
  private String questionText;
  @NotNull
  private Integer isRequired;
  @NotNull
  private Integer sortOrder;
  @NotNull
  private Integer status;
  @Valid
  private List<SurveyOptionRequest> options;
}
