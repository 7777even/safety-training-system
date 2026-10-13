package com.sts.course.vo;

import lombok.Data;

/** 教师目录最小公开字段，不承载账号、联系方式或权限。 */
@Data
public class TeacherPublicVO {
  /** 已有教师用户引用。 */
  private Long teacherId;
  /** 可公开姓名。 */
  private String teacherName;
}
