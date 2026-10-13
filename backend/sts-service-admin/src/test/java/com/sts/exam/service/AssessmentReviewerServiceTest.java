package com.sts.exam.service;

import com.sts.common.exception.BusinessException;
import com.sts.exam.entity.AssessmentExecution;
import com.sts.exam.mapper.ExamReviewerMapper;
import com.sts.paper.entity.PaperVersionQuestion;
import com.sts.paper.mapper.PaperVersionQuestionMapper;
import com.sts.question.entity.QuestionVersion;
import com.sts.question.mapper.QuestionVersionMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AssessmentReviewerServiceTest {

  @Test
  void assertCourseReviewersConfigured_rejectsManualQuizWithoutReviewer() {
    ExamReviewerMapper reviewerMapper = mock(ExamReviewerMapper.class);
    PaperVersionQuestionMapper paperQuestionMapper = mock(PaperVersionQuestionMapper.class);
    QuestionVersionMapper questionVersionMapper = mock(QuestionVersionMapper.class);
    AssessmentReviewerService service = new AssessmentReviewerService(
      reviewerMapper, paperQuestionMapper, questionVersionMapper);
    AssessmentExecution execution = execution("COURSE_QUIZ", "PAPER_VERSION");
    PaperVersionQuestion question = new PaperVersionQuestion();
    question.setQuestionVersionId(20L);
    QuestionVersion version = new QuestionVersion();
    version.setGradingMode("MANUAL");
    when(paperQuestionMapper.selectList(any())).thenReturn(List.of(question));
    when(questionVersionMapper.selectById(20L)).thenReturn(version);
    when(reviewerMapper.selectCount(any())).thenReturn(0L);

    assertThatThrownBy(() -> service.assertCourseReviewersConfigured(List.of(execution)))
      .isInstanceOfSatisfying(BusinessException.class, error ->
        org.assertj.core.api.Assertions.assertThat(error.getCode()).isEqualTo(5068));
  }

  @Test
  void assertCourseReviewersConfigured_allowsObjectiveQuizWithoutReviewer() {
    ExamReviewerMapper reviewerMapper = mock(ExamReviewerMapper.class);
    PaperVersionQuestionMapper paperQuestionMapper = mock(PaperVersionQuestionMapper.class);
    QuestionVersionMapper questionVersionMapper = mock(QuestionVersionMapper.class);
    AssessmentReviewerService service = new AssessmentReviewerService(
      reviewerMapper, paperQuestionMapper, questionVersionMapper);
    PaperVersionQuestion question = new PaperVersionQuestion();
    question.setQuestionVersionId(20L);
    QuestionVersion version = new QuestionVersion();
    version.setGradingMode("AUTO");
    when(paperQuestionMapper.selectList(any())).thenReturn(List.of(question));
    when(questionVersionMapper.selectById(20L)).thenReturn(version);

    service.assertCourseReviewersConfigured(List.of(execution("COURSE_QUIZ", "PAPER_VERSION")));

    verifyNoInteractions(reviewerMapper);
  }

  @Test
  void assertCourseReviewersConfigured_treatsEssayAsManualEvenWhenVersionModeIsWrong() {
    ExamReviewerMapper reviewerMapper = mock(ExamReviewerMapper.class);
    PaperVersionQuestionMapper paperQuestionMapper = mock(PaperVersionQuestionMapper.class);
    QuestionVersionMapper questionVersionMapper = mock(QuestionVersionMapper.class);
    AssessmentReviewerService service = new AssessmentReviewerService(
      reviewerMapper, paperQuestionMapper, questionVersionMapper);
    PaperVersionQuestion question = new PaperVersionQuestion();
    question.setQuestionVersionId(20L);
    question.setQuestionType("ESSAY");
    when(paperQuestionMapper.selectList(any())).thenReturn(List.of(question));
    when(reviewerMapper.selectCount(any())).thenReturn(0L);

    assertThatThrownBy(() -> service.assertCourseReviewersConfigured(
      List.of(execution("COURSE_QUIZ", "PAPER_VERSION"))))
      .isInstanceOfSatisfying(BusinessException.class, error ->
        org.assertj.core.api.Assertions.assertThat(error.getCode()).isEqualTo(5068));
    verifyNoInteractions(questionVersionMapper);
  }

  private AssessmentExecution execution(String sourceType, String resourceType) {
    AssessmentExecution execution = new AssessmentExecution();
    execution.setAssessmentExecutionId(10L);
    execution.setSourceType(sourceType);
    execution.setResourceType(resourceType);
    execution.setResourceId(30L);
    return execution;
  }
}
