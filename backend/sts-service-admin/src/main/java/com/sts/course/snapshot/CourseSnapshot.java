package com.sts.course.snapshot;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@JsonPropertyOrder({
  "courseId", "courseCode", "courseName", "coverFileId", "introduction", "teachingObjective",
  "syllabus", "assessmentMethod", "contentMode", "credit", "pointMode", "pointValue",
  "sourceDownloadMode", "isSpeedPlayAllowed", "isStudyAnticheatEnabled", "isStudyDescriptionEnabled",
  "isWatermarkEnabled", "status"
})
public class CourseSnapshot {
  private Long courseId;
  private String courseCode;
  private String courseName;
  /** 本机构主讲教师用户ID，旧课程可空。 */
  private Long teacherUserId;
  /** 主讲教师姓名，学习详情使用冻结值。 */
  private String teacherName;
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
  private Integer status;
}
