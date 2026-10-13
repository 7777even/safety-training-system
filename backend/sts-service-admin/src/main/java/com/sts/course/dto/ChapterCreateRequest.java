package com.sts.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ChapterCreateRequest {
  @NotNull(message = "课程ID不能为空")
  private Long courseId;

  @NotBlank(message = "章节名称不能为空")
  private String chapterName;

  /** 父章节ID，0表示根级；更新未传时保留父级。 */
  @jakarta.validation.constraints.PositiveOrZero(message = "父章节ID不能为负数")
  private Long parentId = 0L;

  private Integer sortOrder = 0;
  private Integer status = 1;
}
