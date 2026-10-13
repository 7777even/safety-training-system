package com.sts.course.service;

import com.sts.common.exception.BusinessException;
import com.sts.course.entity.CourseEnrollment;
import com.sts.course.entity.CourseOffering;
import com.sts.course.entity.CourseVersion;
import com.sts.course.mapper.CourseEnrollmentMapper;
import com.sts.course.mapper.CourseOfferingMapper;
import com.sts.course.mapper.CourseVersionMapper;
import com.sts.course.snapshot.ChapterSnapshot;
import com.sts.course.snapshot.CourseContentSnapshot;
import com.sts.course.snapshot.ItemSnapshot;
import com.sts.exam.entity.ExamCandidate;
import com.sts.exam.service.AssessmentExecutionService;
import com.sts.exam.mapper.ExamCandidateMapper;
import com.sts.training.port.TrainingCourseOfferingCommand;
import com.sts.training.port.TrainingParticipantBinding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TrainingCourseExecutionAdapterTest {

  private CourseOfferingMapper offeringMapper;
  private CourseVersionMapper courseVersionMapper;
  private CourseEnrollmentMapper enrollmentMapper;
  private CourseSnapshotAssembler snapshotAssembler;
  private ExamCandidateMapper examCandidateMapper;
  private AssessmentExecutionService assessmentExecutionService;
  private CourseOfferingService courseOfferingService;
  private TrainingCourseExecutionAdapter adapter;

  @BeforeEach
  void setUp() {
    offeringMapper = mock(CourseOfferingMapper.class);
    courseVersionMapper = mock(CourseVersionMapper.class);
    enrollmentMapper = mock(CourseEnrollmentMapper.class);
    snapshotAssembler = mock(CourseSnapshotAssembler.class);
    examCandidateMapper = mock(ExamCandidateMapper.class);
    assessmentExecutionService = mock(AssessmentExecutionService.class);
    courseOfferingService = mock(CourseOfferingService.class);
    adapter = new TrainingCourseExecutionAdapter(
      offeringMapper, courseVersionMapper, enrollmentMapper,
      snapshotAssembler,
      assessmentExecutionService,
      courseOfferingService,
      examCandidateMapper);
  }

  @Test
  @DisplayName("ensureOffering：使用冻结版本而非课程当前版本")
  void ensureOffering_usesFrozenVersion() {
    when(offeringMapper.selectOne(any())).thenReturn(null);
    CourseVersion version = new CourseVersion();
    version.setCourseVersionId(50L);
    version.setContentSnapshotJson("{}");
    when(courseVersionMapper.selectById(50L)).thenReturn(version);
    when(offeringMapper.insert(any(CourseOffering.class))).thenAnswer(inv -> {
      ((CourseOffering) inv.getArgument(0)).setCourseOfferingId(888L);
      return 1;
    });
    TrainingCourseOfferingCommand command = new TrainingCourseOfferingCommand();
    command.setPlanContentId(300L);
    command.setCourseId(10L);
    command.setCourseVersionId(50L);
    command.setOfferingName("安全课");
    command.setRequired(true);

    Long offeringId = adapter.ensureOffering(command);

    ArgumentCaptor<CourseOffering> captor = ArgumentCaptor.forClass(CourseOffering.class);
    verify(offeringMapper).insert(captor.capture());
    assertThat(captor.getValue().getCourseVersionId()).isEqualTo(50L);
    assertThat(captor.getValue().getEnrollmentType()).isEqualTo("ASSIGNED");
    assertThat(captor.getValue().getPublishStatus()).isEqualTo("DRAFT");
    assertThat(captor.getValue().getPublishedTime()).isNull();
    assertThat(captor.getValue().getPlanContentId()).isEqualTo(300L);
    assertThat(offeringId).isEqualTo(888L);
    verify(courseVersionMapper, never()).selectOne(any());
    verify(assessmentExecutionService).ensureCourseExecutions(any(), any());
  }

  @Test
  @DisplayName("ensureOffering：已有开课仍补齐测验执行，不在此时校验阅卷人")
  void ensureOffering_existingStillEnsuresExecutionsWithoutReviewerAssert() {
    CourseOffering existing = new CourseOffering();
    existing.setCourseOfferingId(888L);
    existing.setCourseVersionId(50L);
    when(offeringMapper.selectOne(any())).thenReturn(existing);
    CourseVersion version = new CourseVersion();
    version.setCourseVersionId(50L);
    version.setContentSnapshotJson("{}");
    when(courseVersionMapper.selectById(50L)).thenReturn(version);
    TrainingCourseOfferingCommand command = new TrainingCourseOfferingCommand();
    command.setPlanContentId(300L);
    command.setCourseVersionId(50L);

    Long offeringId = adapter.ensureOffering(command);

    assertThat(offeringId).isEqualTo(888L);
    verify(offeringMapper, never()).insert(any(CourseOffering.class));
    verify(assessmentExecutionService).ensureCourseExecutions(any(), any());
  }

  @Test
  @DisplayName("publishOffering：调用同一套开课发布")
  void publishOffering_delegatesToCourseOfferingService() {
    CourseOffering offering = new CourseOffering();
    offering.setCourseOfferingId(888L);
    offering.setVersion(2);
    when(offeringMapper.selectById(888L)).thenReturn(offering);

    adapter.publishOffering(888L);

    verify(courseOfferingService).publishOffering(eq(888L), argThat(req -> req.getVersion() == 2));
  }

  @Test
  @DisplayName("findOfferingIdByPlanContentId：按计划内容返回已落下的开课")
  void findOfferingIdByPlanContentId_returnsExisting() {
    CourseOffering existing = new CourseOffering();
    existing.setCourseOfferingId(888L);
    when(offeringMapper.selectOne(any())).thenReturn(existing);

    assertThat(adapter.findOfferingIdByPlanContentId(300L)).isEqualTo(888L);
  }

  @Test
  @DisplayName("ensureEnrollments：来源不一致时拒绝")
  void ensureEnrollments_rejectsMismatchedExisting() {
    CourseEnrollment existing = new CourseEnrollment();
    existing.setEnrollmentSource("ASSIGN");
    existing.setUserId(7L);
    when(enrollmentMapper.selectOne(any())).thenReturn(existing);
    TrainingParticipantBinding binding = new TrainingParticipantBinding();
    binding.setTrainingParticipantId(2L);
    binding.setUserId(7L);

    assertThatThrownBy(() -> adapter.ensureEnrollments(888L, 300L, List.of(binding)))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 8146);
    verify(enrollmentMapper, never()).insert(any(CourseEnrollment.class));
  }

  @Test
  @DisplayName("reconcileTrainingFinalScore：只为培训绑定的已完成课程补齐测验平均分")
  void reconcileTrainingFinalScore_updatesOnlyTrainingEnrollment() {
    CourseEnrollment enrollment = new CourseEnrollment();
    enrollment.setCourseEnrollmentId(11L);
    enrollment.setTrainingParticipantId(22L);
    enrollment.setLearningStatus("COMPLETED");
    when(enrollmentMapper.selectOne(any())).thenReturn(enrollment);
    CourseOffering offering = new CourseOffering();
    offering.setCourseOfferingId(888L);
    offering.setPlanContentId(300L);
    offering.setCourseVersionId(50L);
    offering.setCompletionRule("ALL_REQUIRED_ITEMS");
    when(offeringMapper.selectById(888L)).thenReturn(offering);
    CourseVersion version = new CourseVersion();
    version.setCourseVersionId(50L);
    version.setContentSnapshotJson("{}");
    when(courseVersionMapper.selectById(50L)).thenReturn(version);
    when(snapshotAssembler.parseContentSnapshot("{}")).thenReturn(trainingQuizSnapshot());
    when(examCandidateMapper.selectList(any())).thenReturn(List.of(candidate(101L, "80"), candidate(102L, "90")));

    adapter.reconcileTrainingFinalScore(888L, 22L);

    verify(enrollmentMapper).updateById(argThat((CourseEnrollment updated) ->
      updated.getFinalScore().compareTo(new java.math.BigDecimal("85.00")) == 0));
  }

  /** 构造两个必修课程测验，用于验证历史补账与实时计算共用的成绩口径。 */
  private CourseContentSnapshot trainingQuizSnapshot() {
    ItemSnapshot first = new ItemSnapshot();
    first.setCourseItemId(101L);
    first.setItemType("QUIZ");
    first.setIsRequired(1);
    ItemSnapshot second = new ItemSnapshot();
    second.setCourseItemId(102L);
    second.setItemType("QUIZ");
    second.setIsRequired(1);
    ChapterSnapshot chapter = new ChapterSnapshot();
    chapter.setItems(List.of(first, second));
    CourseContentSnapshot snapshot = new CourseContentSnapshot();
    snapshot.setChapters(List.of(chapter));
    return snapshot;
  }

  /** 构造已生效的课程测验当前正式分。 */
  private ExamCandidate candidate(Long courseItemId, String finalScore) {
    ExamCandidate candidate = new ExamCandidate();
    candidate.setCourseItemId(courseItemId);
    candidate.setFinalScore(new java.math.BigDecimal(finalScore));
    return candidate;
  }
}
