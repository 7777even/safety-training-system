package com.sts.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LiveSessionUpdateRequest {
  @NotBlank(message = "直播名称不能为空")
  private String liveName;

  @NotNull(message = "讲师用户ID不能为空")
  private Long teacherUserId;

  @NotBlank(message = "直播链接不能为空")
  private String liveUrl;

  @NotNull(message = "直播开始时间不能为空")
  private LocalDateTime startTime;

  @NotNull(message = "直播结束时间不能为空")
  private LocalDateTime endTime;

  @NotBlank(message = "直播状态不能为空")
  private String liveStatus;

  @NotNull(message = "版本号不能为空")
  private Integer version;

  @NotNull(message = "启用状态不能为空")
  private Integer status;
}
