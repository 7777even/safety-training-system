package com.sts.training.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class TrainingTemplateVO {
  private Long trainingTemplateId;
  private Long organizationId;
  private String templateCode;
  private String templateName;
  private Long templateCategoryId;
  private Long trainingTypeId;
  private Long trainingStandardId;
  private String description;
  private String publishStatus;
  private Integer status;
  private Integer version;
  private LocalDateTime createTime;
  private List<TrainingTemplateStageVO> stages;
}
