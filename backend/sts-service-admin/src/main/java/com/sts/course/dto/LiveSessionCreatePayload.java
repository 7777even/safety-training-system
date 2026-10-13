package com.sts.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LiveSessionCreatePayload {
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

}
