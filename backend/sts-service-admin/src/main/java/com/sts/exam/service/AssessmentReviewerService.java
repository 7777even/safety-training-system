package com.sts.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sts.common.exception.BusinessException;
import com.sts.exam.entity.AssessmentExecution;
import com.sts.exam.entity.ExamReviewer;
import com.sts.exam.mapper.ExamReviewerMapper;
import com.sts.paper.entity.PaperVersionQuestion;
import com.sts.paper.mapper.PaperVersionQuestionMapper;
import com.sts.question.mapper.QuestionVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AssessmentReviewerService {
  private final ExamReviewerMapper reviewerMapper;
  private final PaperVersionQuestionMapper paperQuestionMapper;
  private final QuestionVersionMapper questionVersionMapper;

  public boolean requiresManualReview(AssessmentExecution execution) {
    if (!"PAPER_VERSION".equals(execution.getResourceType())) return false;
    return paperRequiresManualReview(execution.getResourceId());
  }

  /**
   * 冻结试卷版本是否包含非自动判分题目。
   */
  public boolean paperRequiresManualReview(Long paperVersionId) {
    if (paperVersionId == null) {
      return false;
    }
    List<PaperVersionQuestion> questions = paperQuestionMapper.selectList(
      new LambdaQueryWrapper<PaperVersionQuestion>()
        .eq(PaperVersionQuestion::getPaperVersionId, paperVersionId));
    return questions.stream().anyMatch(question -> {
      if ("ESSAY".equals(question.getQuestionType())) return true;
      var version = questionVersionMapper.selectById(question.getQuestionVersionId());
      return version != null && "MANUAL".equals(version.getGradingMode());
    });
  }

  public void assertCourseReviewersConfigured(List<AssessmentExecution> executions) {
    for (AssessmentExecution execution : executions) {
      if (!"COURSE_QUIZ".equals(execution.getSourceType()) || !requiresManualReview(execution)) continue;
      Long count = reviewerMapper.selectCount(new LambdaQueryWrapper<ExamReviewer>()
                                                .eq(ExamReviewer::getAssessmentExecutionId,
                                                    execution.getAssessmentExecutionId()));
      if (count == null || count == 0) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, 5068, "含主观题的课程测验必须配置阅卷人");
      }
    }
  }
}
