package com.sts.course.service;

import cn.dev33.satoken.stp.StpUtil;
import com.sts.admin.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.common.exception.BusinessException;
import com.sts.course.dto.CourseAssessmentResultQuery;
import com.sts.course.entity.CourseAssessmentResult;
import com.sts.course.entity.CourseOffering;
import com.sts.course.mapper.CourseAssessmentResultMapper;
import com.sts.course.mapper.CourseMapper;
import com.sts.course.mapper.CourseOfferingMapper;
import com.sts.course.mapper.CourseVersionMapper;
import com.sts.course.vo.CourseAssessmentResultVO;
import com.sts.exam.dto.ExamReviewTaskQuery;
import com.sts.exam.dto.ExamReviewersReplaceRequest;
import com.sts.exam.dto.ReviewAnswerRequest;
import com.sts.exam.entity.AssessmentExecution;
import com.sts.exam.entity.ExamReviewer;
import com.sts.exam.mapper.ExamAttemptQueryMapper;
import com.sts.exam.mapper.ExamReviewerMapper;
import com.sts.exam.mapper.ExamReviewerQueryMapper;
import com.sts.exam.service.AssessmentExecutionService;
import com.sts.exam.service.ExamAttemptService;
import com.sts.exam.vo.ExamReviewTaskVO;
import com.sts.exam.vo.ExamReviewerVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CourseAssessmentManagementService {
  private final CourseOfferingMapper offeringMapper;
  private final CourseMapper courseMapper;
  private final CourseVersionMapper versionMapper;
  private final CourseSnapshotAssembler snapshotAssembler;
  private final AssessmentExecutionService executionService;
  private final ExamReviewerMapper reviewerMapper;
  private final ExamReviewerQueryMapper reviewerQueryMapper;
  private final ExamAttemptQueryMapper attemptQueryMapper;
  private final CourseAssessmentResultMapper resultMapper;
  private final SysUserMapper userMapper;
  private final ExamAttemptService attemptService;

  public List<ExamReviewerVO> listReviewers(Long offeringId, Long itemId) {
    AssessmentExecution execution = findCourseExecution(offeringId, itemId, false);
    if (execution == null) return List.of();
    assertQuiz(execution);
    return reviewerQueryMapper.selectReviewers(execution.getAssessmentExecutionId());
  }

  @Transactional
  public void replaceReviewers(Long offeringId, Long itemId, ExamReviewersReplaceRequest request) {
    AssessmentExecution execution = requireQuizExecution(offeringId, itemId, true);
    reviewerMapper.delete(new LambdaQueryWrapper<ExamReviewer>()
                            .eq(ExamReviewer::getAssessmentExecutionId,
                                execution.getAssessmentExecutionId()));
    for (Long userId : request.getReviewerUserIds()) {
      ExamReviewer reviewer = new ExamReviewer();
      reviewer.setAssessmentExecutionId(execution.getAssessmentExecutionId());
      reviewer.setReviewerUserId(userId);
      reviewer.setAssignmentSource(request.getAssignmentSource() == null ? "USER" : request.getAssignmentSource());
      reviewerMapper.insert(reviewer);
    }
  }

  public PageResult<ExamReviewTaskVO> pageReviewTasks(Long offeringId, Long itemId,
                                                      PageRequest<ExamReviewTaskQuery> request) {
    AssessmentExecution execution = requireQuizExecution(offeringId, itemId, false);
    Page<ExamReviewTaskVO> page = attemptQueryMapper.selectReviewTaskPage(
      new Page<>(request.getPageNum(), request.getPageSize()), StpUtil.getLoginIdAsLong(),
      execution.getAssessmentExecutionId(), true, request.getQuery());
    return new PageResult<>(page.getRecords(), page.getTotal());
  }

  public PageResult<CourseAssessmentResultVO> pageResults(Long offeringId, Long itemId,
                                                          PageRequest<CourseAssessmentResultQuery> request) {
    AssessmentExecution execution = requireCourseExecution(offeringId, itemId);
    Page<CourseAssessmentResult> page = resultMapper.selectPage(
      new Page<>(request.getPageNum(), request.getPageSize()),
      new LambdaQueryWrapper<CourseAssessmentResult>()
        .eq(CourseAssessmentResult::getAssessmentExecutionId, execution.getAssessmentExecutionId())
        .eq(CourseAssessmentResult::getCourseOfferingId, offeringId)
        .eq(CourseAssessmentResult::getCourseItemId, itemId)
        .orderByDesc(CourseAssessmentResult::getEffectiveTime)
        .orderByDesc(CourseAssessmentResult::getCourseAssessmentResultId));
    return new PageResult<>(page.getRecords().stream().map(this::toResultVO).toList(), page.getTotal());
  }

  public void reviewAnswer(Long offeringId, Long itemId, Long answerId, ReviewAnswerRequest request) {
    requireQuizExecution(offeringId, itemId, false);
    attemptService.reviewCourseAnswer(offeringId, itemId, answerId, request);
  }

  private AssessmentExecution requireQuizExecution(Long offeringId, Long itemId, boolean createForConfiguration) {
    AssessmentExecution execution = requireCourseExecution(offeringId, itemId, createForConfiguration);
    assertQuiz(execution);
    return execution;
  }

  private void assertQuiz(AssessmentExecution execution) {
    if (!"COURSE_QUIZ".equals(execution.getSourceType())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 5084, "内容项不是课程测验");
    }
  }

  private AssessmentExecution requireCourseExecution(Long offeringId, Long itemId) {
    return requireCourseExecution(offeringId, itemId, false);
  }

  private AssessmentExecution requireCourseExecution(Long offeringId, Long itemId,
                                                     boolean createForConfiguration) {
    AssessmentExecution execution = findCourseExecution(offeringId, itemId, createForConfiguration);
    if (execution == null) {
      throw new BusinessException(HttpStatus.CONFLICT, 7062, "课程考核尚未生成执行对象");
    }
    return execution;
  }

  private AssessmentExecution findCourseExecution(Long offeringId, Long itemId,
                                                  boolean createForConfiguration) {
    CourseOffering offering = offeringMapper.selectById(offeringId);
    if (offering == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 5061, "开课不存在");
    }
    if (offering.getCourseVersionId() == null) {
      var course = courseMapper.selectById(offering.getCourseId());
      if (course == null || course.getCurrentVersionId() == null) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, 5083, "开课没有可执行的课程版本");
      }
      var version = versionMapper.selectById(course.getCurrentVersionId());
      if (version == null) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, 5083, "课程版本快照不存在");
      }
      offering.setCourseVersionId(version.getCourseVersionId());
      if (createForConfiguration) {
        executionService.ensureCourseExecutions(
          offering, snapshotAssembler.parseContentSnapshot(version.getContentSnapshotJson()));
      }
    }
    return executionService.findCourse(offeringId, offering.getCourseVersionId(), itemId);
  }

  private CourseAssessmentResultVO toResultVO(CourseAssessmentResult result) {
    CourseAssessmentResultVO vo = new CourseAssessmentResultVO();
    BeanUtils.copyProperties(result, vo);
    // 结果事实只保存用户 ID，管理端展示时补充当前用户名称。
    var user = result.getUserId() == null ? null : userMapper.selectById(result.getUserId());
    vo.setLearnerName(user == null ? null : user.getUserName());
    return vo;
  }
}
