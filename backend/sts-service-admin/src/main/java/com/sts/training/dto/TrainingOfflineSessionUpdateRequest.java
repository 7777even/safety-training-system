package com.sts.training.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TrainingOfflineSessionUpdateRequest {
  @NotNull private Integer version;
  @NotBlank private String sessionName;
  private Long teacherUserId;
  @NotBlank private String location;
  @NotNull private LocalDateTime startTime;
  @NotNull private LocalDateTime endTime;
  private String sessionStatus;
  @NotNull private Integer status;
}
