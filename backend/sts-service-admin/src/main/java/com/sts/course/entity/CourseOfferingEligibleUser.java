package com.sts.course.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_course_offering_eligible_user")
public class CourseOfferingEligibleUser extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long offeringEligibleUserId;
  private Long organizationId;
  private Long courseOfferingId;
  private Long userId;
}
