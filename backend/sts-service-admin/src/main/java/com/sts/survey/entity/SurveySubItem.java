package com.sts.survey.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_survey_sub_item")
public class SurveySubItem extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long surveySubItemId;
  private Long surveyQuestionId;
  private String itemName;
  private String dataType;
  private Integer maxStar;
  private Integer sortOrder;
  private Integer status;
}
