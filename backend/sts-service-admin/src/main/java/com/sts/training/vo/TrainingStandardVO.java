package com.sts.training.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class TrainingStandardVO {
  private Long trainingStandardId;
  private Long organizationId;
  private String standardCode;
  private String standardName;
  private Long trainingTypeId;
  private String standardVersion;
  private LocalDate validFrom;
  private LocalDate validTo;
  private String publishStatus;
  private Integer status;
  private Integer version;
  private LocalDateTime createTime;
  private List<TrainingStandardRuleVO> rules;
}
