package com.sts.course.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseAuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_course_assessment_result")
public class CourseAssessmentResult extends BaseAuditEntity {
  @TableId(type = IdType.AUTO)
  private Long courseAssessmentResultId;
  private Long organizationId;
  private Long assessmentExecutionId;
  private Long examCandidateId;
  private Long examAttemptId;
  private Long courseEnrollmentId;
  private Long courseOfferingId;
  private Long courseItemId;
  private Long userId;
  private String sourceType;
  private Integer attemptNo;
  private BigDecimal finalScore;
  private Integer isPassed;
  private Integer correctCount;
  private Integer wrongCount;
  private LocalDateTime effectiveTime;
}
