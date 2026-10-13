package com.sts.evaluation.dto;

import lombok.Data;

@Data
public class EvaluationFormQuery {
  private String formCode;
  private String formName;
  private String formType;
  private Integer status;
}
