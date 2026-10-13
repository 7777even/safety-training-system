package com.sts.training.dto;

import lombok.Data;

@Data
public class TrainingTypeQuery {
  private String typeCode;
  private String typeName;
  private Integer status;
}
