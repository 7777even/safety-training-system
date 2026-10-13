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
@TableName("sts_course_item_progress")
public class CourseItemProgress extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long itemProgressId;
  private Long organizationId;
  private Long courseEnrollmentId;
  private Long courseItemId;
  private String progressStatus;
  private Integer studySeconds;
  private Integer lastPositionSeconds;
  private Integer lastPageNo;
  private Integer attemptCount;
  private BigDecimal bestScore;
  private LocalDateTime firstStartTime;
  private LocalDateTime completedTime;
  private LocalDateTime lastStudyTime;
}
