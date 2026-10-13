package com.sts.course.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 课程多层章节，父关系用于目录编排，祖先路径随移动同步更新。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_course_chapter")
public class CourseChapter extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long chapterId;
  private Long courseId;
  private Long parentId;
  private String ancestors;
  private String chapterName;
  private Integer sortOrder;
  private Integer status;
}
