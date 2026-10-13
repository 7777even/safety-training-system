package com.sts.training.vo;

import lombok.Data;

@Data
public class TrainingReferenceResourceVO {
  private String resourceType;
  private Long resourceId;
  private Long versionId;
  private String resourceCode;
  private String resourceName;
  private Long organizationId;
  private String organizationName;
  private String publishStatus;
  private Integer status;
  private Boolean executable;
}
