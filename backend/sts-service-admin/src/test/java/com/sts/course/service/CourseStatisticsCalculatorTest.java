package com.sts.course.service;

import com.sts.course.entity.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/** 课程参与按学员去重，有效秒数和会话次数保持事实口径。 */
class CourseStatisticsCalculatorTest {
  @Test void repeatedOfferingsCountOnePersonButSumRealStudySeconds() {
    var a=new CourseEnrollment(); a.setCourseEnrollmentId(1L); a.setUserId(7L); a.setTotalStudySeconds(120); a.setProgressPercent(BigDecimal.valueOf(50));
    var b=new CourseEnrollment(); b.setCourseEnrollmentId(2L); b.setUserId(7L); b.setTotalStudySeconds(60); b.setProgressPercent(BigDecimal.valueOf(100)); b.setLearningStatus("COMPLETED");
    var session=new CourseStudySession(); session.setCourseEnrollmentId(1L); session.setValidStudySeconds(30);
    var result=CourseStatisticsCalculator.summary(List.of(a,b),List.of(session),java.util.Set.of());
    assertThat(result.enrolledCount()).isEqualTo(1);
    assertThat(result.participatedCount()).isEqualTo(1);
    assertThat(result.totalStudySeconds()).isEqualTo(180);
    assertThat(result.studyCount()).isEqualTo(1);
    assertThat(result.completedCount()).isEqualTo(1);
  }
}
