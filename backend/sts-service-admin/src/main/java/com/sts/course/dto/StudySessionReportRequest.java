package com.sts.course.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StudySessionReportRequest {
  @NotNull(message = "报名ID不能为空")
  private Long enrollmentId;
  @NotNull(message = "内容项ID不能为空")
  private Long courseItemId;
  @NotBlank(message = "客户端追踪标识不能为空")
  private String clientTraceId;
  @NotBlank(message = "设备类型不能为空")
  private String deviceType;
  @NotBlank(message = "终端标识不能为空")
  private String deviceId;
  @NotNull(message = "开始时间不能为空")
  private LocalDateTime startedTime;
  private LocalDateTime endedTime;
  @NotNull(message = "有效学习秒数不能为空")
  @Min(value = 0, message = "有效学习秒数不能为负")
  private Integer validStudySeconds;
  private Integer startPositionSeconds;
  private Integer endPositionSeconds;
  private Integer startPageNo;
  private Integer endPageNo;
  @NotBlank(message = "结束原因不能为空")
  private String endReason;
}
