package com.sts.training.vo;

import lombok.Data;

@Data
public class TrainingPlanEvaluationFormVO {
  private Long planEvaluationFormId;
  private Long trainingPlanId;
  private Long formVersionId;
  private Long evaluationFormId;
  private String formName;
  private Integer versionNo;
  private String publishedStatus;
  private String usageType;
  private Integer isRequired;
  private Integer status;
}
