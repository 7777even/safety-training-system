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
@TableName("sts_survey_response")
public class SurveyResponse extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long surveyResponseId;
  private Long organizationId;
  private Long surveyInstanceId;
  private Long surveyId;
  private Long surveyVersionId;
  private Long surveyParticipantId;
  private Long userId;
  private Integer isAnonymous;
  private String responseStatus;
  private LocalDateTime startedTime;
  private LocalDateTime submittedTime;
  private String clientIp;
  private String deviceType;
}
