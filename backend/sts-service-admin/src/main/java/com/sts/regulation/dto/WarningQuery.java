package com.sts.regulation.dto;

import lombok.Data;

@Data
public class WarningQuery {
  private String warningStatus;
  private Long regulatedOrgId;
  private Long trainingTypeId;
}
