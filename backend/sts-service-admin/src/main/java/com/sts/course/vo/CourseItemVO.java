package com.sts.course.vo;

import lombok.Data;

@Data
public class CourseItemVO {
  private Long courseItemId;
  private Long courseId;
  private Long chapterId;
  private String itemType;
  private String itemTitle;
  private Long assetId;
  /** 课件关联的同课程测验ID，可空；多个课件可复用同一测验。 */
  private Long linkedQuizItemId;
  /** 结业测评标记，仅QUIZ可启用，旧版本空值沿用普通测验语义。 */
  private Integer isFinalAssessment;
  /** 结业及格线覆盖值，空值沿用已发布试卷。 */
  private java.math.BigDecimal passScore;
  private Long questionBankId;
  private String questionBankName;
  private Long paperVersionId;
  private Long paperId;
  private String paperName;
  private Integer paperVersionNo;
  private String paperPublishedStatus;
  private Integer isRequired;
  private Integer minimumStudySeconds;
  private Integer sortOrder;
  private Integer isLockedAfterParticipation;
  private Integer status;
  private Integer version;
  private LiveSessionVO liveSession;
}
