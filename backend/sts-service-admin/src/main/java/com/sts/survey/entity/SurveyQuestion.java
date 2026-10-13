package com.sts.survey.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_survey_question")
public class SurveyQuestion extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long surveyQuestionId;
  private Long surveyId;
  private String questionType;
  private String questionText;
  private Integer isRequired;
  private Integer isOtherAllowed;
  private Integer isDescriptionAllowed;
  private String dataType;
  private Integer maxStar;
  private Integer maxUploadCount;
  private Integer sortOrder;
  private Integer status;
}
