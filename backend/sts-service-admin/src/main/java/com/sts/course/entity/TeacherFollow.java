package com.sts.course.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.sts.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 本人教师关注关系，直接引用已有教师用户。 */
@Data @EqualsAndHashCode(callSuper = true) @TableName("sts_teacher_follow")
public class TeacherFollow extends BaseEntity {
  /** 关注记录主键。 */
  @TableId(type = IdType.AUTO) private Long teacherFollowId;
  /** 机构、学员和教师组成唯一关系。 */
  private Long organizationId;
  private Long userId;
  private Long teacherId;
  /** 0取消、1关注，不逻辑删除以保证重复操作幂等。 */
  private Integer isFollowed;
  /** 教师失效后仍可辨识并取消的姓名快照。 */
  private String teacherNameSnapshot;
}
