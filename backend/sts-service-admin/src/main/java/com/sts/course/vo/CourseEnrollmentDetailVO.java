package com.sts.course.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CourseEnrollmentDetailVO {
  private Long courseEnrollmentId;
  private Long courseOfferingId;
  private Long userId;
  private String enrollmentSource;
  private LocalDateTime enrolledTime;
  private String learningStatus;
  private BigDecimal progressPercent;
  private Integer totalStudySeconds;
  /** 结业合格时冻结的认可秒数，旧报名空值沿用累计学习时长。 */
  private Integer recognizedStudySeconds;
  private BigDecimal creditAwarded;
  private LocalDateTime completedTime;
  private LocalDateTime lastStudyTime;
  /** 每个考核内容项最近一次生效结果，供本人课程报告消费。 */
  private java.util.List<CourseAssessmentResultVO> assessmentResults;
  private CourseLearningCourseVO course;
  private List<LearnerCourseDownloadAssetVO> downloadAssets;
  private List<CourseItemProgressVO> itemProgresses;
  private CourseEvaluationVO evaluation;
  private List<CourseStudySessionSummaryVO> recentSessions;
}
