package com.sts.course.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_course_study_session")
public class CourseStudySession extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long studySessionId;
  private Long organizationId;
  private Long courseEnrollmentId;
  private Long courseItemId;
  private Long userId;
  private String deviceType;
  /** 终端标识：客户端生成并持久化，用于同一终端多账户同时学习的互斥检测。 */
  private String deviceId;
  private LocalDateTime startedTime;
  private LocalDateTime endedTime;
  private Integer validStudySeconds;
  private Integer startPositionSeconds;
  private Integer endPositionSeconds;
  private Integer startPageNo;
  private Integer endPageNo;
  private String endReason;
  private String clientTraceId;
}
