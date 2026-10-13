package com.sts.survey.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_survey_answer")
public class SurveyAnswer extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long surveyAnswerId;
  private Long surveyResponseId;
  private Long surveyQuestionId;
  private Long surveySubItemId;
  private Integer subItemNo;
  private String answerText;
  private BigDecimal numericValue;
  private LocalDate dateValue;
  private LocalTime timeValue;
  private String otherText;
  private String descriptionText;
  private Integer sortValue;
}
