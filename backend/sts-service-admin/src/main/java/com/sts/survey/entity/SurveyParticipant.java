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
@TableName("sts_survey_participant")
public class SurveyParticipant extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long surveyParticipantId;
  private Long organizationId;
  private Long surveyInstanceId;
  private Long surveyId;
  private Long surveyVersionId;
  private Long userId;
  private String participantStatus;
  private LocalDateTime firstOpenTime;
  private LocalDateTime submittedTime;
}
