package com.sts.course.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CourseLearningCourseVO {
  private Long courseId;
  private String courseCode;
  private String courseName;
  /** 本机构主讲教师用户ID，旧课程可空。 */
  private Long teacherUserId;
  /** 主讲教师姓名，学习详情使用冻结值。 */
  private String teacherName;
  private String introduction;
  private String teachingObjective;
  private String syllabus;
  private String assessmentMethod;
  private BigDecimal credit;
  private String pointMode;
  private BigDecimal pointValue;
  private String contentMode;
  private String sourceDownloadMode;
  /** 是否允许倍速播放：1 允许、0 不允许。学员学习页据此决定是否放开倍速控件。 */
  private Integer isSpeedPlayAllowed;
  /** 是否启用学习防作弊：1 启用、0 不启用。学员答题页据此开启切屏与复制粘贴监控。 */
  private Integer isStudyAnticheatEnabled;
  /** 是否显示学时说明：1 显示、0 不显示。 */
  private Integer isStudyDescriptionEnabled;
  /** 是否启用学习水印：1 启用、0 不启用。学员学习页据此叠加当前用户水印。 */
  private Integer isWatermarkEnabled;
  private String coverUrl;
}
