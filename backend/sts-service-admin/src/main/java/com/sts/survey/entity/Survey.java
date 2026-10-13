package com.sts.survey.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_survey_survey")
public class Survey extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long surveyId;
  private Long organizationId;
  private String surveyCode;
  private String surveyName;
  private Long currentVersionId;
  private String surveyType;
  private Long coverFileId;
  private String description;
  private String closingText;
  private Integer isAnonymous;
  private String contentStatus;
  private Integer status;
}
