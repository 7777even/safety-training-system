package com.sts.training.dto;

import lombok.Data;

@Data
public class TrainingStandardQuery {
  private String standardCode;
  private String standardName;
  private Long trainingTypeId;
  private String publishStatus;
  private Integer status;
}
