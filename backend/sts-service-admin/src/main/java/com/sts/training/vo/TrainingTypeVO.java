package com.sts.training.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TrainingTypeVO {
  private Long trainingTypeId;
  private Long organizationId;
  private String typeCode;
  private String typeName;
  private String workType;
  private String tag;
  private Long coverFileId;
  private BigDecimal initialHours;
  private BigDecimal reviewHours;
  private BigDecimal onlineMaxRatio;
  private Integer status;
  private String description;
  private Integer version;
  private LocalDateTime createTime;
}
