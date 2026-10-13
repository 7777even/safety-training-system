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
@TableName("sts_course_enrollment")
public class CourseEnrollment extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long courseEnrollmentId;
  private Long organizationId;
  private Long courseOfferingId;
  private Long userId;
  private Long trainingParticipantId;
  private String enrollmentSource;
  private Long sourceBusinessId;
  private LocalDateTime enrolledTime;
  private String learningStatus;
  private BigDecimal progressPercent;
  private Integer totalStudySeconds;
  /** 结业合格时冻结的认可秒数，旧报名空值沿用累计学习时长。 */
  private Integer recognizedStudySeconds;
  private BigDecimal creditAwarded;
  private BigDecimal finalScore;
  private LocalDateTime completedTime;
  private LocalDateTime lastStudyTime;
  private String resultSnapshotJson;
}
