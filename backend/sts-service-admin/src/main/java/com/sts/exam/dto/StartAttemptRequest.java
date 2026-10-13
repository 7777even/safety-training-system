package com.sts.exam.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StartAttemptRequest {
  @NotNull private Long examId;
  @JsonIgnore
  private Long assessmentExecutionId;
  @NotNull private Long userId;
  /**
   * 仅课程服务内部调用时写入；通用开考接口不得接受 COURSE 上下文。
   */
  @JsonIgnore
  private Long courseOfferingId;
  @JsonIgnore
  private Long courseItemId;
  @JsonIgnore
  private Long courseEnrollmentId;
  /**
   * 仅培训服务内部调用时写入；通用开考接口不得接受 TRAINING 上下文。
   */
  @JsonIgnore
  private Long trainingParticipantId;
  @NotBlank private String clientAttemptToken;
  /**
   * 仅补考内部调用写入；通用开考 HTTP 不得指定作答类型。
   */
  @JsonIgnore
  private String attemptType;
  private String platform;
  private String operatingSystem;
  private String browser;
  private String clientIp;
}
