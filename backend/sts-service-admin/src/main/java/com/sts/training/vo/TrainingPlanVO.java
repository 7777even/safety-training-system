package com.sts.training.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class TrainingPlanVO {
  private Long trainingPlanId;
  private Long organizationId;
  private String planCode;
  private String planName;
  private Long trainingTypeId;
  private String trainingNature;
  private BigDecimal applicableRequiredHours;
  private Long trainingStandardId;
  private Long sourceTemplateId;
  private Long sourceOwnerOrganizationId;
  private String trainingTypeSnapshotJson;
  private String trainingStandardSnapshotJson;
  private String templateSnapshotJson;
  private String sourceSnapshotHash;
  private Long managerUserId;
  private LocalDateTime startTime;
  private LocalDateTime endTime;
  private String location;
  private String description;
  private String publishStatus;
  private LocalDateTime publishedTime;
  private Integer status;
  private Integer version;
  private LocalDateTime createTime;
  private List<TrainingPlanStageVO> stages;
  private List<TrainingPlanEvaluationFormVO> evaluationForms = List.of();
}
