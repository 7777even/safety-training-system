package com.sts.exam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AnticheatEventRequest {
  @NotBlank private String eventType;
  @NotNull private LocalDateTime eventTime;
  @NotNull private Integer eventCount;
  @NotBlank private String actionTaken;
  private Long evidenceFileId;
  private String clientPayloadJson;
}
