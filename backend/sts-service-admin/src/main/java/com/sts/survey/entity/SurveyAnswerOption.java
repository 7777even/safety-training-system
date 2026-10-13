package com.sts.survey.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sts_survey_answer_option")
public class SurveyAnswerOption {
  @TableId(type = IdType.AUTO)
  private Long answerOptionId;
  private Long surveyAnswerId;
  private Long surveyOptionId;
  private Integer sortOrder;
  private Long createBy;
  private LocalDateTime createTime;
}
