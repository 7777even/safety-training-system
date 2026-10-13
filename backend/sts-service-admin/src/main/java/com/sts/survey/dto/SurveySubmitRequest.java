package com.sts.survey.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 本人提交答卷；用户 ID 从登录身份派生。
 */
@Data
public class SurveySubmitRequest {
  private String deviceType;
  @Valid
  @NotNull
  private List<SurveyAnswerRequest> answers;
}
