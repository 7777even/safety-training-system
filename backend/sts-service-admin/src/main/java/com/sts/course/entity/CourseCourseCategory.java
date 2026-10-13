package com.sts.course.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseAuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 课程与分类关联（B 类关联表：只建立/解绑关系，物理删除，业务唯一键 {@code uk_course_category}）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_course_course_category")
public class CourseCourseCategory extends BaseAuditEntity {
  @TableId(type = IdType.AUTO)
  private Long courseCategoryRelId;
  private Long courseId;
  private Long courseCategoryId;
}
