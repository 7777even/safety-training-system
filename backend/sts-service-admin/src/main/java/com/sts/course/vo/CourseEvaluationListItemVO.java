package com.sts.course.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 学员查看同次开课评价的公开字段，不包含评价人标识。 */
@Data
public class CourseEvaluationListItemVO {
  /** 评分，范围为 0～5。 */
  private BigDecimal rating;
  /** 评价内容，可为空。 */
  private String content;
  /** 是否匿名展示。 */
  private Integer isAnonymous;
  /** 匿名时固定为“匿名学员”。 */
  private String displayName;
  /** 提交时间；匿名项仅保留日期。 */
  private String submittedTime;
}
