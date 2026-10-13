package com.sts.course.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.sts.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_course_course")
public class Course extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long courseId;
  private Long organizationId;
  private String courseCode;
  private String courseName;
  /** 本机构主讲教师用户ID，旧课程可空。 */
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private Long teacherUserId;
  private Long currentVersionId;
  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private Long coverFileId;
  private String introduction;
  private String teachingObjective;
  private String syllabus;
  private String assessmentMethod;
  private String contentMode;
  private BigDecimal credit;
  private String pointMode;
  private BigDecimal pointValue;
  private String sourceDownloadMode;
  private Integer isSpeedPlayAllowed;
  private Integer isStudyAnticheatEnabled;
  private Integer isStudyDescriptionEnabled;
  private Integer isWatermarkEnabled;
  private String publishStatus;
  private LocalDateTime publishedTime;
  private Integer status;
}
