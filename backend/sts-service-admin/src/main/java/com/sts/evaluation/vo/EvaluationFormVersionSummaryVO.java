package com.sts.evaluation.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EvaluationFormVersionSummaryVO {
  private Long formVersionId;
  private Long evaluationFormId;
  private Integer versionNo;
  private String publishedStatus;
  private Long publishedBy;
  private LocalDateTime publishedTime;
  private String versionNote;
}
