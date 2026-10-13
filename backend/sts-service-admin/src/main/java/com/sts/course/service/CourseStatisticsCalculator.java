package com.sts.course.service;
import com.sts.course.entity.*;
import com.sts.course.vo.CourseStatisticsVO.Summary;
import java.math.*;
import java.util.*;
/** 纯计算课程参与，报名不等于参加学习。 */
public final class CourseStatisticsCalculator {
  private CourseStatisticsCalculator() {}
  /** 跨开班按人去重，学习秒数按报名事实相加，次数仅计有效会话。 */
  public static Summary summary(List<CourseEnrollment> rows,List<CourseStudySession> sessions,Set<Long> assessedEnrollmentIds) {
    var ids=new HashSet<Long>(); rows.forEach(e->ids.add(e.getCourseEnrollmentId()));
    long participants=rows.stream().filter(e->(e.getTotalStudySeconds()!=null && e.getTotalStudySeconds()>0) || assessedEnrollmentIds.contains(e.getCourseEnrollmentId()))
      .map(CourseEnrollment::getUserId).distinct().count();
    var progress=rows.stream().map(CourseEnrollment::getProgressPercent).filter(Objects::nonNull).toList();
    return new Summary(rows.stream().map(CourseEnrollment::getUserId).distinct().count(),participants,
      rows.stream().filter(e->"COMPLETED".equals(e.getLearningStatus())).map(CourseEnrollment::getUserId).distinct().count(),
      rows.stream().mapToLong(e->e.getTotalStudySeconds()==null?0:e.getTotalStudySeconds()).sum(),
      sessions.stream().filter(s->ids.contains(s.getCourseEnrollmentId()) && s.getValidStudySeconds()!=null && s.getValidStudySeconds()>0).count(),
      progress.isEmpty()?null:progress.stream().reduce(BigDecimal.ZERO,BigDecimal::add).divide(BigDecimal.valueOf(progress.size()),2,RoundingMode.HALF_UP));
  }
}
