package com.sts.course.mapper;

import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.course.entity.CourseAssessmentResult;
import com.sts.course.support.CourseMapperItConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = CourseMapperItConfig.class)
@ActiveProfiles("test")
@Transactional
class CourseAssessmentResultMapperIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private CourseAssessmentResultMapper mapper;

  @Test
  void effectiveResultIsImmutableAndIdempotentByAttempt() {
    CourseAssessmentResult first = result(400L, 1);
    mapper.insert(first);

    assertThat(first.getCourseAssessmentResultId()).isNotNull();
    assertThatThrownBy(() -> mapper.insert(result(400L, 1)))
      .isInstanceOf(DuplicateKeyException.class);

    CourseAssessmentResult repeatedPractice = result(401L, 2);
    mapper.insert(repeatedPractice);
    assertThat(repeatedPractice.getCourseAssessmentResultId()).isNotNull();
  }

  private CourseAssessmentResult result(Long attemptId, int attemptNo) {
    CourseAssessmentResult result = new CourseAssessmentResult();
    result.setOrganizationId(2L);
    result.setAssessmentExecutionId(300L);
    result.setExamCandidateId(350L);
    result.setExamAttemptId(attemptId);
    result.setCourseEnrollmentId(1000L);
    result.setCourseOfferingId(100L);
    result.setCourseItemId(101L);
    result.setUserId(99L);
    result.setSourceType("COURSE_BANK_PRACTICE");
    result.setAttemptNo(attemptNo);
    result.setFinalScore(new BigDecimal("8"));
    result.setIsPassed(1);
    result.setCorrectCount(8);
    result.setWrongCount(2);
    result.setEffectiveTime(LocalDateTime.now());
    return result;
  }
}
