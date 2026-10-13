package com.sts.course.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CourseVO {
  private Long courseId;
  private String courseCode;
  private String courseName;
  /** 本机构主讲教师用户ID，旧课程可空。 */
  private Long teacherUserId;
  /** 主讲教师姓名，学习详情使用冻结值。 */
  private String teacherName;
  private Long currentVersionId;
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
  private Integer version;
  private List<Long> categoryIds;
}
