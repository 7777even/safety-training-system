package com.sts.course.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_course_category")
public class CourseCategory extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long courseCategoryId;
  private Long organizationId;
  private Long parentId;
  private String ancestors;
  private String categoryName;
  private String description;
  private Integer sortOrder;
  private Integer status;
}
