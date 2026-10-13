package com.sts.survey.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_survey_option")
public class SurveyOption extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long surveyOptionId;
  private Long surveyQuestionId;
  private String optionKey;
  private String optionContent;
  private Integer sortOrder;
  private Integer status;
}
