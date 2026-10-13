package com.sts.training.service;

import com.sts.common.exception.BusinessException;
import com.sts.training.dto.TrainingContentExecutionConfig;
import com.sts.training.entity.*;
import com.sts.training.mapper.*;
import com.sts.training.port.TrainingAssessmentExecutionPort;
import com.sts.training.port.TrainingCourseExecutionPort;
import com.sts.training.port.TrainingCourseOfferingCommand;
import com.sts.training.port.TrainingExamExecutionCommand;
import com.sts.training.vo.TrainingExecutionLinkVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TrainingExecutionProvisionerTest {

  private TrainingPlanMapper planMapper;
  private TrainingPlanStageMapper stageMapper;
  private TrainingPlanContentMapper contentMapper;
  private TrainingParticipantMapper participantMapper;
  private TrainingExecutionLinkMapper executionLinkMapper;
  private TrainingOfflineSessionMapper offlineSessionMapper;
  private TrainingCourseExecutionPort coursePort;
  private TrainingAssessmentExecutionPort assessmentPort;
  private TrainingContentConfigCodec codec;
  private TrainingQuestionPoolSnapshotService questionPoolSnapshotService;
  private TrainingExecutionProvisioner provisioner;

  @org.junit.jupiter.api.AfterEach void clearTenant() { com.sts.admin.tenant.TenantContext.clear(); }

  @BeforeEach
  void setUp() {
    planMapper = mock(TrainingPlanMapper.class);
    com.sts.admin.tenant.TenantContext.setTenantId(1L);
    when(planMapper.lockForApproval(any(),any())).thenAnswer(inv -> planMapper.selectById(inv.getArgument(0)));
    stageMapper = mock(TrainingPlanStageMapper.class);
    contentMapper = mock(TrainingPlanContentMapper.class);
    participantMapper = mock(TrainingParticipantMapper.class);
    executionLinkMapper = mock(TrainingExecutionLinkMapper.class);
    offlineSessionMapper = mock(TrainingOfflineSessionMapper.class);
    coursePort = mock(TrainingCourseExecutionPort.class);
    assessmentPort = mock(TrainingAssessmentExecutionPort.class);
    codec = new TrainingContentConfigCodec(new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules());
    questionPoolSnapshotService = mock(TrainingQuestionPoolSnapshotService.class);
    provisioner = new TrainingExecutionProvisioner(
      planMapper, stageMapper, contentMapper, participantMapper,
      executionLinkMapper, offlineSessionMapper, coursePort, assessmentPort, codec,
      mock(TrainingResultSyncService.class), questionPoolSnapshotService,
      new com.fasterxml.jackson.databind.ObjectMapper());
  }

  @Test
  @DisplayName("provision COURSE：冻结版本开课并为参训人报名，executionId 为开课 ID")
  void provisionCourse_usesFrozenVersionAndTrainingEnrollments() {
    publishedPlanAndStage();
    TrainingPlanContent content = content(300L, "COURSE");
    content.setResourceId(10L);
    content.setResourceVersionId(50L);
    content.setIsRequired(1);
    content.setExecutionConfigJson("{\"completionRule\":\"ALL_ITEMS\"}");
    when(contentMapper.selectById(300L)).thenReturn(content);
    when(coursePort.ensureOffering(any())).thenReturn(888L);
    when(participantMapper.selectList(any())).thenReturn(List.of(participant(2L, 7L)));
    when(executionLinkMapper.selectOne(any())).thenReturn(null);
    when(executionLinkMapper.insert(any(TrainingExecutionLink.class))).thenAnswer(inv -> {
      ((TrainingExecutionLink) inv.getArgument(0)).setExecutionLinkId(1L);
      return 1;
    });

    TrainingExecutionLinkVO vo = provisioner.provision(100L, 300L);

    ArgumentCaptor<TrainingCourseOfferingCommand> cmd = ArgumentCaptor.forClass(TrainingCourseOfferingCommand.class);
    verify(coursePort).ensureOffering(cmd.capture());
    assertThat(cmd.getValue().getCourseVersionId()).isEqualTo(50L);
    assertThat(cmd.getValue().getCourseId()).isEqualTo(10L);
    assertThat(cmd.getValue().getCompletionRule()).isEqualTo("ALL_ITEMS");
    verify(coursePort).publishOffering(888L);
    verify(coursePort).ensureEnrollments(eq(888L), eq(300L), argThat(list ->
                                                                       list.size() == 1 && list.getFirst().getTrainingParticipantId().equals(
                                                                         2L)));
    assertThat(vo.getExecutionId()).isEqualTo(888L);
    assertThat(vo.getExecutionType()).isEqualTo("COURSE");
    verify(assessmentPort, never()).ensureExamExecution(any());
  }

  @Test
  @DisplayName("provision COURSE：缺阅卷人时不报名、不写执行关联")
  void provisionCourse_missingReviewersSkipsEnrollmentAndLink() {
    publishedPlanAndStage();
    TrainingPlanContent content = content(300L, "COURSE");
    content.setResourceId(10L);
    content.setResourceVersionId(50L);
    when(contentMapper.selectById(300L)).thenReturn(content);
    when(coursePort.ensureOffering(any())).thenReturn(888L);
    doThrow(new BusinessException(org.springframework.http.HttpStatus.BAD_REQUEST, 5068,
                                  "含主观题的课程测验必须配置阅卷人"))
      .when(coursePort).publishOffering(888L);

    assertThatThrownBy(() -> provisioner.provision(100L, 300L))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 5068);
    verify(coursePort).ensureOffering(any());
    verify(coursePort, never()).ensureEnrollments(any(), any(), any());
    verify(executionLinkMapper, never()).insert(any(TrainingExecutionLink.class));
  }

  @Test
  @DisplayName("provision EXAM：executionId 为 AssessmentExecution ID，不使用来源 examId")
  void provisionExam_usesAssessmentExecutionId() {
    publishedPlanAndStage();
    TrainingPlanContent content = content(301L, "EXAM");
    content.setResourceId(900L);
    content.setResourceVersionId(80L);
    content.setAssessmentMode("QUIZ");
    String snapshot = v2ExamSnapshotJson();
    content.setResourceSnapshotJson(snapshot);
    content.setResourceSnapshotHash(sha256(snapshot));
    when(contentMapper.selectById(301L)).thenReturn(content);
    when(assessmentPort.ensureExamExecution(any())).thenReturn(777L);
    when(participantMapper.selectList(any())).thenReturn(List.of(participant(2L, 7L)));
    when(executionLinkMapper.selectOne(any())).thenReturn(null);
    when(executionLinkMapper.insert(any(TrainingExecutionLink.class))).thenReturn(1);
    TrainingExecutionLinkVO vo = provisioner.provision(100L, 301L);

    ArgumentCaptor<TrainingExamExecutionCommand> cmd = ArgumentCaptor.forClass(TrainingExamExecutionCommand.class);
    verify(assessmentPort).ensureExamExecution(cmd.capture());
    assertThat(cmd.getValue().getSourceExamId()).isEqualTo(900L);
    assertThat(cmd.getValue().getPaperVersionId()).isEqualTo(80L);
    assertThat(cmd.getValue().getAssessmentKind()).isEqualTo("QUIZ");
    assertThat(cmd.getValue().getResourceSnapshotHash()).isEqualTo(sha256(snapshot));
    assertThat(cmd.getValue().getResponsibleUserId()).isEqualTo(7L);
    assertThat(cmd.getValue().getReviewers()).extracting("reviewerUserId").containsExactly(7L, 8L);
    verify(assessmentPort).ensureCandidates(eq(777L), eq(301L), anyList());
    assertThat(vo.getExecutionId()).isEqualTo(777L);
    assertThat(vo.getExecutionId()).isNotEqualTo(900L);
  }

  @Test
  @DisplayName("provision EXAM：schemaVersion=1 或缺必需字段时拒绝，不回读来源")
  void provisionExam_rejectsLegacySnapshot() {
    publishedPlanAndStage();
    TrainingPlanContent content = content(301L, "EXAM");
    content.setResourceId(900L);
    content.setResourceVersionId(80L);
    content.setAssessmentMode("QUIZ");
    String snapshot = "{\"contentType\":\"EXAM\",\"resourceVersionId\":80,\"durationSeconds\":3600,"
      + "\"passScore\":60,\"questionOrderMode\":\"IMPORT\",\"multiSelectScoring\":\"ALL_OR_NOTHING\","
      + "\"assessmentMode\":\"QUIZ\"}";
    content.setResourceSnapshotJson(snapshot);
    content.setResourceSnapshotHash(sha256(snapshot));
    when(contentMapper.selectById(301L)).thenReturn(content);

    assertThatThrownBy(() -> provisioner.provision(100L, 301L))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 8142)
      .hasMessageContaining("301");
    verify(assessmentPort, never()).ensureExamExecution(any());
  }

  @Test
  @DisplayName("provision EXAM：运行参数来自发布快照，hash 不一致则失败")
  void provisionExam_rejectsMismatchedSnapshotHash() {
    publishedPlanAndStage();
    TrainingPlanContent content = content(301L, "EXAM");
    content.setResourceId(900L);
    content.setResourceVersionId(80L);
    content.setAssessmentMode("QUIZ");
    content.setResourceSnapshotJson(
      "{\"contentType\":\"EXAM\",\"resourceVersionId\":80,\"durationSeconds\":3600,"
        + "\"passScore\":60,\"questionOrderMode\":\"IMPORT\",\"multiSelectScoring\":\"ALL_OR_NOTHING\","
        + "\"assessmentMode\":\"QUIZ\"}");
    content.setResourceSnapshotHash("deadbeef");
    when(contentMapper.selectById(301L)).thenReturn(content);

    assertThatThrownBy(() -> provisioner.provision(100L, 301L))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 8142)
      .hasMessageContaining("301");
    verify(assessmentPort, never()).ensureExamExecution(any());
  }

  @Test
  @DisplayName("provision QUESTION_BANK：assessmentKind 为 BANK_PRACTICE")
  void provisionQuestionBank_createsBankPractice() {
    publishedPlanAndStage();
    TrainingPlanContent content = content(302L, "QUESTION_BANK");
    content.setResourceId(70L);
    content.setExecutionConfigJson(
      "{\"durationSeconds\":600,\"passScore\":60,\"maxAttempts\":2,"
        + "\"drawRules\":[{\"questionType\":\"SINGLE\",\"drawCount\":5}]}");
    when(contentMapper.selectById(302L)).thenReturn(content);
    when(assessmentPort.ensureExamExecution(any())).thenReturn(555L);
    when(participantMapper.selectList(any())).thenReturn(List.of());
    when(executionLinkMapper.selectOne(any())).thenReturn(null);
    when(executionLinkMapper.insert(any(TrainingExecutionLink.class))).thenReturn(1);
    com.sts.training.port.TrainingQuestionPoolItem poolItem = new com.sts.training.port.TrainingQuestionPoolItem();
    poolItem.setQuestionId(1L);
    when(questionPoolSnapshotService.load(content)).thenReturn(
      new TrainingQuestionPoolSnapshotService.FrozenPool("pool-hash", List.of(poolItem)));

    TrainingExecutionLinkVO vo = provisioner.provision(100L, 302L);

    ArgumentCaptor<TrainingExamExecutionCommand> cmd = ArgumentCaptor.forClass(TrainingExamExecutionCommand.class);
    verify(assessmentPort).ensureExamExecution(cmd.capture());
    assertThat(cmd.getValue().getAssessmentKind()).isEqualTo("BANK_PRACTICE");
    assertThat(cmd.getValue().getQuestionBankId()).isEqualTo(70L);
    assertThat(cmd.getValue().getDurationSeconds()).isEqualTo(600);
    assertThat(cmd.getValue().getQuestionPoolHash()).isEqualTo("pool-hash");
    assertThat(vo.getExecutionType()).isEqualTo("QUESTION_BANK");
    assertThat(vo.getExecutionId()).isEqualTo(555L);
  }

  @Test
  @DisplayName("provision OFFLINE：仅使用冻结的教师、地点和时间")
  void provisionOffline_usesFrozenSessionRules() {
    publishedPlanAndStage();
    TrainingPlanContent content = content(303L, "OFFLINE");
    TrainingContentExecutionConfig config = new TrainingContentExecutionConfig();
    config.setTeacherUserId(44L);
    config.setLocation("车间A");
    config.setStartTime(LocalDateTime.of(2026, 9, 1, 9, 0));
    config.setEndTime(LocalDateTime.of(2026, 9, 1, 12, 0));
    config.setPassScore(new BigDecimal("60"));
    content.setExecutionConfigJson(codec.toJson(config));
    when(contentMapper.selectById(303L)).thenReturn(content);
    when(offlineSessionMapper.selectOne(any())).thenReturn(null);
    when(offlineSessionMapper.insert(any(TrainingOfflineSession.class))).thenAnswer(inv -> {
      ((TrainingOfflineSession) inv.getArgument(0)).setOfflineSessionId(66L);
      return 1;
    });

    TrainingExecutionLinkVO vo = provisioner.provision(100L, 303L);

    ArgumentCaptor<TrainingOfflineSession> session = ArgumentCaptor.forClass(TrainingOfflineSession.class);
    verify(offlineSessionMapper).insert(session.capture());
    assertThat(session.getValue().getTeacherUserId()).isEqualTo(44L);
    assertThat(session.getValue().getLocation()).isEqualTo("车间A");
    assertThat(vo.getExecutionId()).isEqualTo(66L);
    assertThat(vo.getExecutionType()).isEqualTo("OFFLINE");
    verifyNoInteractions(coursePort, assessmentPort);
  }

  @Test
  @DisplayName("provision：内容不属于计划返回 8145")
  void provision_rejectsForeignContent() {
    TrainingPlan plan = new TrainingPlan();
    plan.setTrainingPlanId(100L);
    plan.setPublishStatus("PUBLISHED");
    when(planMapper.selectById(100L)).thenReturn(plan);
    TrainingPlanContent content = content(3L, "COURSE");
    content.setPlanStageId(4L);
    when(contentMapper.selectById(3L)).thenReturn(content);
    TrainingPlanStage stage = new TrainingPlanStage();
    stage.setPlanStageId(4L);
    stage.setTrainingPlanId(9L);
    when(stageMapper.selectById(4L)).thenReturn(stage);

    assertThatThrownBy(() -> provisioner.provision(100L, 3L))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 8145);
    verifyNoInteractions(executionLinkMapper);
  }

  private void publishedPlanAndStage() {
    TrainingPlan plan = new TrainingPlan();
    plan.setTrainingPlanId(100L);
    plan.setOrganizationId(1L);
    plan.setPublishStatus("PUBLISHED");
    plan.setStartTime(LocalDateTime.of(2026, 8, 1, 8, 0));
    plan.setEndTime(LocalDateTime.of(2026, 8, 31, 18, 0));
    plan.setLocation("园区");
    when(planMapper.selectById(100L)).thenReturn(plan);
    TrainingPlanStage stage = new TrainingPlanStage();
    stage.setPlanStageId(200L);
    stage.setTrainingPlanId(100L);
    stage.setStartTime(LocalDateTime.of(2026, 8, 10, 9, 0));
    stage.setEndTime(LocalDateTime.of(2026, 8, 10, 17, 0));
    stage.setLocation("教室");
    when(stageMapper.selectById(200L)).thenReturn(stage);
  }

  private static String v2ExamSnapshotJson() {
    return "{"
      + "\"schemaVersion\":2,"
      + "\"sourceExamId\":900,"
      + "\"examCode\":\"E-1\","
      + "\"examName\":\"理论考试\","
      + "\"paperVersionId\":80,"
      + "\"assessmentMode\":\"QUIZ\","
      + "\"durationSeconds\":3600,"
      + "\"passScore\":60,"
      + "\"passRatio\":0.6,"
      + "\"questionOrderMode\":\"IMPORT\","
      + "\"multiSelectScoring\":\"ALL_OR_NOTHING\","
      + "\"credit\":0,"
      + "\"examCategoryId\":3,"
      + "\"coverFileId\":9,"
      + "\"responsibleUserId\":7,"
      + "\"isNoticeEnabled\":0,"
      + "\"noticeContent\":null,"
      + "\"requiresManualReview\":true,"
      + "\"policy\":{\"isWatermarkEnabled\":0,\"isPcAllowed\":1,\"isAppAllowed\":1,\"isH5Allowed\":1,"
      + "\"switchLimit\":null,\"isSplitScreenForceSubmit\":0,\"isCopyRefreshDevtoolDisabled\":0,"
      + "\"inactivitySeconds\":null,\"inactivityCountdownSeconds\":null,\"minimumAnswerSeconds\":null,"
      + "\"isRetakeEnabled\":0,\"retakeScope\":null,\"retakeStartTime\":null,\"retakeEndTime\":null,"
      + "\"retakeMaxCount\":0,\"retakeScoreMode\":\"HIGHEST\",\"isRetakeRecordVisible\":1,"
      + "\"passPoint\":0,\"failPoint\":0,\"absentPoint\":0,\"lateForbidSeconds\":0,"
      + "\"answerDetailMode\":\"SUBMITTED\",\"isAnalysisVisible\":1,\"isScoreVisible\":1,\"isRankingEnabled\":0},"
      + "\"reviewers\":[{\"reviewerUserId\":7,\"assignmentSource\":\"USER\"},"
      + "{\"reviewerUserId\":8,\"assignmentSource\":\"USER\"}]"
      + "}";
  }

  private static TrainingPlanContent content(Long id, String type) {
    TrainingPlanContent content = new TrainingPlanContent();
    content.setPlanContentId(id);
    content.setPlanStageId(200L);
    content.setContentType(type);
    content.setContentName(type);
    return content;
  }

  private static TrainingParticipant participant(Long id, Long userId) {
    TrainingParticipant participant = new TrainingParticipant();
    participant.setTrainingParticipantId(id);
    participant.setTrainingPlanId(100L);
    participant.setUserId(userId);
    return participant;
  }

  private static String sha256(String input) {
    try {
      java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder();
      for (byte b : hash) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }
}
