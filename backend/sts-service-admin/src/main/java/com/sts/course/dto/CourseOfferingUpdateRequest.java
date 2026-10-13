package com.sts.course.dto;

import com.sts.person.dto.PersonSelectionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CourseOfferingUpdateRequest {
  @NotNull(message = "版本号不能为空")
  private Integer version;

  @NotBlank(message = "开课名称不能为空")
  private String offeringName;

  private LocalDateTime startTime;
  private LocalDateTime endTime;

  @NotBlank(message = "开课类型不能为空")
  @Pattern(regexp = "REQUIRED|ELECTIVE", message = "开课类型仅支持 REQUIRED 或 ELECTIVE")
  private String courseType;

  @NotBlank(message = "报名类型不能为空")
  @Pattern(regexp = "ASSIGNED|OPEN", message = "报名类型仅支持 ASSIGNED 或 OPEN")
  private String enrollmentType;

  @NotBlank(message = "报名资格类型不能为空")
  @Pattern(regexp = "ALL|SELECTED", message = "报名资格类型仅支持 ALL 或 SELECTED")
  private String eligibilityType;

  @Valid
  private PersonSelectionRequest eligibilitySelection;

  @Pattern(regexp = "ALL_REQUIRED_ITEMS|ALL_ITEMS", message = "完成规则仅支持 ALL_REQUIRED_ITEMS 或 ALL_ITEMS")
  private String completionRule;

  private Integer status;
}
