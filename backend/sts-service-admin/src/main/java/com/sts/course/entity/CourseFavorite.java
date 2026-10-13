package com.sts.course.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.sts.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 54与58共用的本人课程收藏，取消使用状态更新以支持幂等重加。 */
@Data @EqualsAndHashCode(callSuper = true) @TableName("sts_course_favorite")
public class CourseFavorite extends BaseEntity {
  /** 收藏记录主键。 */
  @TableId(type = IdType.AUTO) private Long courseFavoriteId;
  /** 机构、本人及课程组成唯一关系。 */
  private Long organizationId;
  private Long userId;
  private Long courseId;
  /** 0取消、1收藏。 */
  private Integer isFavorite;
  /** 课程失效后仍能辨识与取消的名称快照。 */
  private String courseNameSnapshot;
}
