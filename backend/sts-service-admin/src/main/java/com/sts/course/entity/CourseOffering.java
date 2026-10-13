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
@TableName("sts_course_offering")
public class CourseOffering extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long courseOfferingId;
  private Long organizationId;
  private String offeringCode;
  private Long courseId;
  private Long courseVersionId;
  private Long planContentId;
  private String offeringName;
  private LocalDateTime startTime;
  private LocalDateTime endTime;
  private String courseType;
  private String enrollmentType;
  private String eligibilityType;
  private String completionRule;
  private String publishStatus;
  private LocalDateTime publishedTime;
  private Integer status;
}
