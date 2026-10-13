package com.sts.training.dto;

import lombok.Data;

@Data
public class TrainingTemplateQuery {
  private String templateCode;
  private String templateName;
  private Long templateCategoryId;
  private Long trainingTypeId;
  private String publishStatus;
  private Integer status;
}
