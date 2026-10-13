package com.sts.course.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseAuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 课程不可变发布版本（C 类：仅 createBy/createTime + 发布字段，无 update/deleted/version）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_course_version")
public class CourseVersion extends BaseAuditEntity {
  @TableId(type = IdType.AUTO)
  private Long courseVersionId;
  private Long courseId;
  private Integer versionNo;
  private String contentSnapshotJson;
  private String snapshotHash;
  private String versionNote;
  private Long publishedBy;
  private LocalDateTime publishedTime;
}
