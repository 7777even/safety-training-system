package com.sts.course.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_course_evaluation")
public class CourseEvaluation extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long courseEvaluationId;
  private Long organizationId;
  private Long courseOfferingId;
  private Long courseEnrollmentId;
  private Long userId;
  private BigDecimal rating;
  private String content;
  private Integer isAnonymous;
  private LocalDateTime submittedTime;
  private Integer status;
}
