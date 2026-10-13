package com.sts.course.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sts.common.exception.BusinessException;
import com.sts.course.dto.CourseOfferingPublishRequest;
import com.sts.course.entity.CourseEnrollment;
import com.sts.course.entity.CourseOffering;
import com.sts.course.entity.CourseVersion;
import com.sts.course.mapper.CourseEnrollmentMapper;
import com.sts.course.mapper.CourseOfferingMapper;
import com.sts.course.mapper.CourseVersionMapper;
import com.sts.exam.entity.AssessmentExecution;
import com.sts.exam.entity.ExamCandidate;
import com.sts.exam.mapper.ExamCandidateMapper;
import com.sts.exam.service.AssessmentExecutionService;
import com.sts.training.port.TrainingCourseExecutionPort;
import com.sts.training.port.TrainingCourseOfferingCommand;
import com.sts.training.port.TrainingCourseResult;
import com.sts.training.port.TrainingParticipantBinding;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 课程域实现培训开课/报名内部端口。培训域不得直接写课程 Mapper。
 */
@Service
@RequiredArgsConstructor
public class TrainingCourseExecutionAdapter implements TrainingCourseExecutionPort {

  private static final String SOURCE_TRAINING = "TRAINING";

  private final CourseOfferingMapper offeringMapper;
  private final CourseVersionMapper courseVersionMapper;
  private final CourseEnrollmentMapper enrollmentMapper;
  private final CourseSnapshotAssembler snapshotAssembler;
  private final AssessmentExecutionService assessmentExecutionService;
  private final CourseOfferingService courseOfferingService;
  private final ExamCandidateMapper examCandidateMapper;

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Long ensureOffering(TrainingCourseOfferingCommand command) {
    CourseOffering existing = findByPlanContent(command.getPlanContentId());
    if (existing != null) {
      if (!Objects.equals(existing.getCourseVersionId(), command.getCourseVersionId())) {
        throw new BusinessException(HttpStatus.CONFLICT, 8149, "已存在开课绑定的课程版本与冻结版本不一致");
      }
      ensureQuizExecutions(existing);
      return existing.getCourseOfferingId();
    }
    CourseVersion version = requireVersion(command.getCourseVersionId());
    CourseOffering offering = new CourseOffering();
    offering.setOrganizationId(command.getOrganizationId());
    offering.setOfferingCode("TRN-C-" + command.getPlanContentId());
    offering.setCourseId(command.getCourseId());
    offering.setCourseVersionId(command.getCourseVersionId());
    offering.setPlanContentId(command.getPlanContentId());
    offering.setOfferingName(command.getOfferingName());
    offering.setStartTime(command.getStartTime());
    offering.setEndTime(command.getEndTime());
    offering.setCourseType(command.isRequired() ? "REQUIRED" : "ELECTIVE");
    offering.setEnrollmentType("ASSIGNED");
    offering.setEligibilityType("ALL");
    offering.setCompletionRule(StringUtils.hasText(command.getCompletionRule())
                                 ? command.getCompletionRule() : "ALL_REQUIRED_ITEMS");
    offering.setPublishStatus("DRAFT");
    offering.setStatus(1);
    try {
      offeringMapper.insert(offering);
    } catch (DuplicateKeyException ex) {
      CourseOffering concurrent = findByPlanContent(command.getPlanContentId());
      if (concurrent == null) {
        throw ex;
      }
      if (!Objects.equals(concurrent.getCourseVersionId(), command.getCourseVersionId())) {
        throw new BusinessException(HttpStatus.CONFLICT, 8149, "已存在开课绑定的课程版本与冻结版本不一致");
      }
      ensureQuizExecutions(concurrent);
      return concurrent.getCourseOfferingId();
    }
    ensureQuizExecutions(offering, version);
    return offering.getCourseOfferingId();
  }

  @Override
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public void publishOffering(Long courseOfferingId) {
    // 生成开课使用独立事务提交；外层 provision 在 MySQL 可重复读下看不见该行，故发布时暂停外层事务。
    CourseOffering offering = offeringMapper.selectById(courseOfferingId);
    if (offering == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 5061, "开课不存在");
    }
    CourseOfferingPublishRequest request = new CourseOfferingPublishRequest();
    request.setVersion(offering.getVersion());
    courseOfferingService.publishOffering(courseOfferingId, request);
  }

  @Override
  public Long findOfferingIdByPlanContentId(Long planContentId) {
    CourseOffering offering = findByPlanContent(planContentId);
    return offering == null ? null : offering.getCourseOfferingId();
  }

  @Override
  @Transactional
  public void ensureEnrollments(Long courseOfferingId, Long planContentId,
                                List<TrainingParticipantBinding> participants) {
    if (participants == null || participants.isEmpty()) {
      return;
    }
    for (TrainingParticipantBinding binding : participants) {
      CourseEnrollment existing = enrollmentMapper.selectOne(
        new LambdaQueryWrapper<CourseEnrollment>()
          .eq(CourseEnrollment::getCourseOfferingId, courseOfferingId)
          .eq(CourseEnrollment::getUserId, binding.getUserId()));
      if (existing != null) {
        assertTrainingEnrollment(existing, binding, planContentId);
        continue;
      }
      CourseEnrollment enrollment = new CourseEnrollment();
      enrollment.setCourseOfferingId(courseOfferingId);
      enrollment.setUserId(binding.getUserId());
      enrollment.setTrainingParticipantId(binding.getTrainingParticipantId());
      enrollment.setEnrollmentSource(SOURCE_TRAINING);
      enrollment.setSourceBusinessId(planContentId);
      enrollment.setEnrolledTime(LocalDateTime.now());
      enrollment.setLearningStatus("NOT_STARTED");
      enrollment.setProgressPercent(BigDecimal.ZERO);
      enrollment.setTotalStudySeconds(0);
      enrollment.setRecognizedStudySeconds(snapshotAssembler.initialRecognizedStudySeconds(
        requireVersion(offeringMapper.selectById(courseOfferingId).getCourseVersionId())));
      enrollment.setCreditAwarded(BigDecimal.ZERO);
      try {
        enrollmentMapper.insert(enrollment);
      } catch (DuplicateKeyException ex) {
        CourseEnrollment concurrent = enrollmentMapper.selectOne(
          new LambdaQueryWrapper<CourseEnrollment>()
            .eq(CourseEnrollment::getCourseOfferingId, courseOfferingId)
            .eq(CourseEnrollment::getUserId, binding.getUserId()));
        if (concurrent == null) {
          throw ex;
        }
        assertTrainingEnrollment(concurrent, binding, planContentId);
      }
    }
  }

  @Override
  public TrainingCourseResult readResult(Long courseOfferingId, Long trainingParticipantId) {
    CourseEnrollment enrollment = enrollmentMapper.selectOne(
      new LambdaQueryWrapper<CourseEnrollment>()
        .eq(CourseEnrollment::getCourseOfferingId, courseOfferingId)
        .eq(CourseEnrollment::getTrainingParticipantId, trainingParticipantId)
        .last("LIMIT 1"));
    if (enrollment == null) {
      return null;
    }
    TrainingCourseResult result = new TrainingCourseResult();
    result.setCourseOfferingId(courseOfferingId);
    result.setSourceRecordId(enrollment.getCourseEnrollmentId());
    result.setSourceStatus(enrollment.getLearningStatus());
    result.setStudySeconds(enrollment.getRecognizedStudySeconds() == null
      ? enrollment.getTotalStudySeconds() : enrollment.getRecognizedStudySeconds());
    result.setCompletedTime(enrollment.getCompletedTime());
    if ("COMPLETED".equals(enrollment.getLearningStatus())) {
      result.setScore(enrollment.getFinalScore());
    }
    return result;
  }

  /** 复用课程域统一的成绩规则，培训域不直接访问课程成绩事实。 */
  @Override
  public void reconcileTrainingFinalScore(Long courseOfferingId, Long trainingParticipantId) {
    CourseEnrollment enrollment = enrollmentMapper.selectOne(new LambdaQueryWrapper<CourseEnrollment>()
      .eq(CourseEnrollment::getCourseOfferingId, courseOfferingId)
      .eq(CourseEnrollment::getTrainingParticipantId, trainingParticipantId)
      .last("LIMIT 1"));
    if (enrollment == null || enrollment.getFinalScore() != null) {
      return;
    }
    CourseOffering offering = offeringMapper.selectById(courseOfferingId);
    if (offering == null) {
      return;
    }
    CourseVersion version = courseVersionMapper.selectById(offering.getCourseVersionId());
    if (version == null || version.getContentSnapshotJson() == null) {
      return;
    }
    BigDecimal score = TrainingCourseScoreCalculator.calculate(enrollment, offering,
      snapshotAssembler.parseContentSnapshot(version.getContentSnapshotJson()),
      examCandidateMapper.selectList(new LambdaQueryWrapper<ExamCandidate>()
        .eq(ExamCandidate::getCourseEnrollmentId, enrollment.getCourseEnrollmentId())));
    if (score != null) {
      enrollment.setFinalScore(score);
      enrollmentMapper.updateById(enrollment);
    }
  }

  @Override
  public boolean existsEnrollmentForParticipant(Long trainingParticipantId) {
    return enrollmentMapper.selectCount(new LambdaQueryWrapper<CourseEnrollment>()
                                          .eq(CourseEnrollment::getTrainingParticipantId, trainingParticipantId)) > 0;
  }

  @Override
  public List<Long> listInProgressLearningParticipantIds(Long courseOfferingId) {
    return enrollmentMapper.selectList(new LambdaQueryWrapper<CourseEnrollment>()
                                         .eq(CourseEnrollment::getCourseOfferingId, courseOfferingId)
                                         .eq(CourseEnrollment::getLearningStatus, "LEARNING"))
      .stream()
      .map(CourseEnrollment::getTrainingParticipantId)
      .filter(Objects::nonNull)
      .distinct()
      .toList();
  }

  private CourseOffering findByPlanContent(Long planContentId) {
    return offeringMapper.selectOne(new LambdaQueryWrapper<CourseOffering>()
                                      .eq(CourseOffering::getPlanContentId, planContentId)
                                      .last("LIMIT 1"));
  }

  private List<AssessmentExecution> ensureQuizExecutions(CourseOffering offering) {
    return ensureQuizExecutions(offering, requireVersion(offering.getCourseVersionId()));
  }

  private List<AssessmentExecution> ensureQuizExecutions(CourseOffering offering, CourseVersion version) {
    return assessmentExecutionService.ensureCourseExecutions(
      offering, snapshotAssembler.parseContentSnapshot(version.getContentSnapshotJson()));
  }

  private CourseVersion requireVersion(Long courseVersionId) {
    CourseVersion version = courseVersionMapper.selectById(courseVersionId);
    if (version == null) {
      throw new BusinessException(HttpStatus.CONFLICT, 8141, "冻结的课程版本不存在");
    }
    return version;
  }

  private void assertTrainingEnrollment(CourseEnrollment existing, TrainingParticipantBinding binding,
                                        Long planContentId) {
    if (!SOURCE_TRAINING.equals(existing.getEnrollmentSource())
      || !Objects.equals(existing.getTrainingParticipantId(), binding.getTrainingParticipantId())
      || !Objects.equals(existing.getSourceBusinessId(), planContentId)) {
      throw new BusinessException(HttpStatus.CONFLICT, 8146, "已有课程报名与当前培训参训人不一致");
    }
  }
}
