package com.sts.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ChapterUpdateRequest {
  @NotBlank(message = "章节名称不能为空")
  private String chapterName;

  /** 父章节ID，0表示根级；更新未传时保留父级。 */
  @jakarta.validation.constraints.PositiveOrZero(message = "父章节ID不能为负数")
  private Long parentId;

  @NotNull(message = "显示顺序不能为空")
  private Integer sortOrder;

  @NotNull(message = "状态不能为空")
  private Integer status;

  @NotNull(message = "版本号不能为空")
  private Integer version;
}
