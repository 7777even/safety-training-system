package com.sts.survey.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_survey_instance")
public class SurveyInstance extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long surveyInstanceId;
  private Long organizationId;
  private String instanceCode;
  private String instanceName;
  private Long surveyId;
  private Long surveyVersionId;
  private Long surveyBindingId;
  /**
   * 投放用途：TRAINING_SURVEY / ORG_EVALUATION。
   */
  private String purpose;
  /**
   * 结果可见方式：ADMIN_ONLY / SUBMITTER_AFTER_SUBMIT。
   */
  private String resultVisibility;
  private String sourceType;
  private Long sourceId;
  private String publishMode;
  private LocalDateTime plannedPublishTime;
  private LocalDateTime publishedTime;
  private LocalDateTime startTime;
  private LocalDateTime endTime;
  private Integer isAnonymousSnapshot;
  private String instanceStatus;
  private Integer status;
}
