package com.sts.exam.dto;

import lombok.Data;

@Data
public class ExamQuery {
  private String examType;
  private String publishStatus;
  private String examName;
  private Integer status;
  /**
   * 培训计划内容 ID；传入时只返回该计划内容生成的考试。
   */
  private Long planContentId;
}
