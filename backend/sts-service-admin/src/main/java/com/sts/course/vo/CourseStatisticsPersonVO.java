package com.sts.course.vo;
import java.math.BigDecimal;
/** 一条报名对应的个人学习记录，保留所属课程和开班。 */
public record CourseStatisticsPersonVO(Long courseEnrollmentId,Long userId,String learnerName,String learnerCode,
 Long courseId,String courseName,Long courseOfferingId,String offeringName,String learningStatus,
 int totalStudySeconds,long studyCount,BigDecimal progressPercent) {}
