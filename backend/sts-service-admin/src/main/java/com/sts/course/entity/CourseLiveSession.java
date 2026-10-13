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
@TableName("sts_course_live_session")
public class CourseLiveSession extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long liveSessionId;
  private Long organizationId;
  private Long courseItemId;
  private String liveName;
  private Long teacherUserId;
  private String liveUrl;
  private LocalDateTime startTime;
  private LocalDateTime endTime;
  private String liveStatus;
  private Integer status;
}
