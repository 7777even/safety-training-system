package com.sts.training.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TrainingOfflineSessionCreateRequest {
  @NotNull private Long trainingPlanId;
  @NotNull private Long planStageId;
  private Long planContentId;
  @NotBlank private String sessionName;
  private Long teacherUserId;
  @NotBlank private String location;
  @NotNull private LocalDateTime startTime;
  @NotNull private LocalDateTime endTime;
  private String sessionStatus = "DRAFT";
  @NotNull private Integer status;
}
