package com.sts.course.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LearnerCourseTaskItemVO {
  private Long courseItemId;
  private String itemType;
  private String itemTitle;
  private Integer isRequired;
  private Integer sortOrder;
  private String progressStatus;
  private Integer studySeconds;
  private Integer lastPositionSeconds;
  private Integer lastPageNo;
  private LocalDateTime completedTime;
  private LocalDateTime lastStudyTime;
  private Boolean learnable;
  private String unavailableReason;
  private Long assetId;
  /** 课件关联的同课程测验ID，可空；多个课件可复用同一测验。 */
  private Long linkedQuizItemId;
  /** 结业测评标记，仅QUIZ可启用，旧版本空值沿用普通测验语义。 */
  private Integer isFinalAssessment;
  /** 首次生效失败且尚未补测时可补测一次。 */
  private Boolean canRetake = false;
  /** 结业及格线覆盖值，空值沿用已发布试卷。 */
  private java.math.BigDecimal passScore;
  private Long questionBankId;
  private Long paperVersionId;
  private Long activeExamAttemptId;
  private Long publishedExamAttemptId;
  private String examTaskStatus;
  private String scorePublishStatus;
  private Integer isPassed;
  private java.math.BigDecimal finalScore;
  private LearnerLiveTaskReferenceVO liveSession;
}
