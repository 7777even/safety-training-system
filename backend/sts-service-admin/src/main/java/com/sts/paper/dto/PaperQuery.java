package com.sts.paper.dto;

import lombok.Data;

@Data
public class PaperQuery {
  private String paperType;
  private Long paperCategoryId;
  private String paperName;
  /**
   * 是否只看存在已发布版本的试卷；true 只返回可直接用于建考试的试卷。
   */
  private Boolean hasPublishedVersion;
  private Integer status;
}
