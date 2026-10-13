package com.sts.training.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TrainingLearnerTaskVO {
  private Long trainingParticipantId;
  private Long trainingPlanId;
  private Long trainingTypeId;
  /** 培训类型名称；类型不存在或已停用时为空，调用方按「未分类」兜底。 */
  private String trainingTypeName;
  private String planName;
  private String coverUrl;
  private LocalDateTime startTime;
  private LocalDateTime endTime;
  private String publishStatus;
  private String participantStatus;
  private String resultStatus;
}
