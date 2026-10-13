package com.sts.evaluation.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EvaluationFormVO {
  private Long evaluationFormId;
  private Long organizationId;
  private String formCode;
  private String formName;
  private String formType;
  private Integer currentVersionNo;
  private Integer status;
  private String description;
  private Integer version;
  private LocalDateTime createTime;
}
