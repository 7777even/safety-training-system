package com.sts.course.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ItemMoveRequest {
  @NotNull(message = "目标章节ID不能为空")
  private Long chapterId;

  @NotNull(message = "排序不能为空")
  private Integer sortOrder;

  @NotNull(message = "版本号不能为空")
  private Integer version;
}
