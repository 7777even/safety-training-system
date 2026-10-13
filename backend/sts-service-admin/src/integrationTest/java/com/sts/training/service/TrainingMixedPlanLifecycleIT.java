package com.sts.training.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sts.admin.entity.SysUser;
import com.sts.admin.mapper.SysUserMapper;
import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.common.exception.BusinessException;
import com.sts.course.entity.Course;
import com.sts.course.entity.CourseEnrollment;
import com.sts.course.entity.CourseOffering;
import com.sts.course.mapper.CourseChapterMapper;
import com.sts.course.mapper.CourseEnrollmentMapper;
import com.sts.course.mapper.CourseMapper;
import com.sts.course.mapper.CourseOfferingMapper;
import com.sts.course.service.CoursePublishService;
import com.sts.course.support.CoursePublishItHelper;
import com.sts.exam.entity.AssessmentExecution;
import com.sts.exam.entity.Exam;
import com.sts.exam.entity.ExamCandidate;
import com.sts.exam.entity.ExamPolicy;
import com.sts.exam.mapper.AssessmentExecutionMapper;
import com.sts.exam.mapper.ExamCandidateMapper;
import com.sts.exam.mapper.ExamMapper;
import com.sts.exam.mapper.ExamPolicyMapper;
import com.sts.org.mapper.OrganizationMapper;
import com.sts.paper.entity.Paper;
import com.sts.paper.entity.PaperVersion;
import com.sts.paper.mapper.PaperMapper;
import com.sts.paper.mapper.PaperVersionMapper;
import com.sts.question.entity.Question;
import com.sts.question.entity.QuestionBank;
import com.sts.question.entity.QuestionOption;
import com.sts.question.entity.QuestionVersion;
import com.sts.question.mapper.QuestionBankMapper;
import com.sts.question.mapper.QuestionMapper;
import com.sts.question.mapper.QuestionOptionMapper;
import com.sts.question.mapper.QuestionVersionMapper;
import com.sts.training.dto.TrainingContentExecutionConfig;
import com.sts.training.dto.TrainingParticipantAssignRequest;
import com.sts.training.dto.TrainingQuestionBankDrawRuleRequest;
import com.sts.training.entity.*;
import com.sts.training.mapper.*;
import com.sts.training.support.TrainingMixedPlanItConfig;
import com.sts.training.vo.TrainingGenerateExecutionsResultVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 一个机构、一名参训人、四类必修内容的真实 MySQL 纵向闭环。
 * <p>
 * Provisioner 使用 REQUIRES_NEW，本类不加 {@code @Transactional}，避免外层事务挡住独立事务读计划。
 */
@SpringBootTest(classes = TrainingMixedPlanItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class TrainingMixedPlanLifecycleIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private TrainingPlanService planService;
  @Autowired
  private TrainingExecutionService executionService;
  @Autowired
  private TrainingPlanLifecycleService lifecycleService;
  @Autowired
  private TrainingResultSyncService resultSyncService;
  @Autowired
  private TrainingContentConfigCodec configCodec;
  @Autowired
  private CoursePublishService coursePublishService;
  @Autowired
  private OrganizationMapper organizationMapper;
  @Autowired
  private SysUserMapper sysUserMapper;
  @Autowired
  private TrainingTypeMapper typeMapper;
  @Autowired
  private TrainingStandardMapper standardMapper;
  @Autowired
  private TrainingStandardRuleMapper standardRuleMapper;
  @Autowired
  private TrainingPlanMapper planMapper;
  @Autowired
  private TrainingPlanStageMapper stageMapper;
  @Autowired
  private TrainingPlanContentMapper contentMapper;
  @Autowired
  private TrainingParticipantMapper participantMapper;
  @Autowired
  private TrainingExecutionLinkMapper executionLinkMapper;
  @Autowired
  private TrainingOfflineSessionMapper offlineSessionMapper;
  @Autowired
  private TrainingOfflineScoreMapper offlineScoreMapper;
  @Autowired
  private TrainingParticipantContentResultMapper resultMapper;
  @Autowired
  private CourseMapper courseMapper;
  @Autowired
  private CourseChapterMapper chapterMapper;
  @Autowired
  private CourseOfferingMapper offeringMapper;
  @Autowired
  private CourseEnrollmentMapper enrollmentMapper;
  @Autowired
  private ExamMapper examMapper;
  @Autowired
  private ExamPolicyMapper examPolicyMapper;
  @Autowired
  private ExamCandidateMapper examCandidateMapper;
  @Autowired
  private AssessmentExecutionMapper assessmentExecutionMapper;
  @Autowired
  private PaperMapper paperMapper;
  @Autowired
  private PaperVersionMapper paperVersionMapper;
  @Autowired
  private QuestionBankMapper questionBankMapper;
  @Autowired
  private QuestionMapper questionMapper;
  @Autowired
  private QuestionVersionMapper questionVersionMapper;
  @Autowired
  private QuestionOptionMapper questionOptionMapper;
  @Autowired
  private JdbcTemplate jdbcTemplate;

  private String uid;
  private Long planId;

  @AfterEach
  void cleanupCommittedRows() {
    if (planId != null) {
      jdbcTemplate.update(
        "DELETE r FROM sts_training_participant_content_result r WHERE r.training_plan_id = ?", planId);
      jdbcTemplate.update(
        "DELETE sc FROM sts_training_offline_score sc INNER JOIN sts_training_offline_session s "
          + "ON sc.offline_session_id = s.offline_session_id WHERE s.training_plan_id = ?", planId);
      jdbcTemplate.update(
        "DELETE c FROM sts_exam_candidate c INNER JOIN sts_training_participant p "
          + "ON c.training_participant_id = p.training_participant_id WHERE p.training_plan_id = ?", planId);
      jdbcTemplate.update(
        "DELETE r FROM sts_exam_reviewer r INNER JOIN sts_assessment_execution e "
          + "ON r.assessment_execution_id = e.assessment_execution_id "
          + "INNER JOIN sts_exam_exam x ON e.exam_id = x.exam_id "
          + "INNER JOIN sts_training_plan_content c ON x.plan_content_id = c.plan_content_id "
          + "INNER JOIN sts_training_plan_stage s ON c.plan_stage_id = s.plan_stage_id "
          + "WHERE s.training_plan_id = ?", planId);
      jdbcTemplate.update(
        "DELETE ep FROM sts_assessment_execution_question_pool ep INNER JOIN sts_assessment_execution e "
          + "ON ep.assessment_execution_id=e.assessment_execution_id INNER JOIN sts_exam_exam x ON e.exam_id=x.exam_id "
          + "INNER JOIN sts_training_plan_content c ON x.plan_content_id=c.plan_content_id "
          + "INNER JOIN sts_training_plan_stage s ON c.plan_stage_id=s.plan_stage_id WHERE s.training_plan_id=?",
        planId);
      jdbcTemplate.update(
        "DELETE e FROM sts_assessment_execution e INNER JOIN sts_exam_exam x ON e.exam_id = x.exam_id "
          + "INNER JOIN sts_training_plan_content c ON x.plan_content_id = c.plan_content_id "
          + "INNER JOIN sts_training_plan_stage s ON c.plan_stage_id = s.plan_stage_id "
          + "WHERE s.training_plan_id = ?", planId);
      jdbcTemplate.update(
        "DELETE p FROM sts_exam_policy p INNER JOIN sts_exam_exam x ON p.exam_id = x.exam_id "
          + "INNER JOIN sts_training_plan_content c ON x.plan_content_id = c.plan_content_id "
          + "INNER JOIN sts_training_plan_stage s ON c.plan_stage_id = s.plan_stage_id "
          + "WHERE s.training_plan_id = ?", planId);
      jdbcTemplate.update(
        "DELETE x FROM sts_exam_exam x INNER JOIN sts_training_plan_content c ON x.plan_content_id = c.plan_content_id "
          + "INNER JOIN sts_training_plan_stage s ON c.plan_stage_id = s.plan_stage_id "
          + "WHERE s.training_plan_id = ?", planId);
      jdbcTemplate.update(
        "DELETE e FROM sts_course_enrollment e INNER JOIN sts_course_offering o "
          + "ON e.course_offering_id = o.course_offering_id "
          + "INNER JOIN sts_training_plan_content c ON o.plan_content_id = c.plan_content_id "
          + "INNER JOIN sts_training_plan_stage s ON c.plan_stage_id = s.plan_stage_id "
          + "WHERE s.training_plan_id = ?", planId);
      jdbcTemplate.update(
        "DELETE o FROM sts_course_offering o INNER JOIN sts_training_plan_content c "
          + "ON o.plan_content_id = c.plan_content_id "
          + "INNER JOIN sts_training_plan_stage s ON c.plan_stage_id = s.plan_stage_id "
          + "WHERE s.training_plan_id = ?", planId);
      jdbcTemplate.update("DELETE FROM sts_training_execution_link WHERE training_plan_id = ?", planId);
      jdbcTemplate.update("DELETE FROM sts_training_plan_question_pool WHERE training_plan_id = ?", planId);
      jdbcTemplate.update("DELETE FROM sts_training_offline_session WHERE training_plan_id = ?", planId);
      jdbcTemplate.update("DELETE FROM sts_training_participant WHERE training_plan_id = ?", planId);
      jdbcTemplate.update(
        "DELETE c FROM sts_training_plan_content c INNER JOIN sts_training_plan_stage s "
          + "ON c.plan_stage_id = s.plan_stage_id WHERE s.training_plan_id = ?", planId);
      jdbcTemplate.update("DELETE FROM sts_training_plan_stage WHERE training_plan_id = ?", planId);
      jdbcTemplate.update("DELETE FROM sts_training_plan_approval WHERE training_plan_id = ?", planId);
      jdbcTemplate.update("DELETE FROM sts_training_plan WHERE training_plan_id = ?", planId);
    }
    if (uid != null) {
      jdbcTemplate.update(
        "DELETE p FROM sts_exam_policy p INNER JOIN sts_exam_exam x ON p.exam_id = x.exam_id "
          + "WHERE x.exam_code = ?", uid + "-EX");
      jdbcTemplate.update("DELETE FROM sts_exam_exam WHERE exam_code = ?", uid + "-EX");
      jdbcTemplate.update(
        "DELETE o FROM sts_question_option o INNER JOIN sts_question_version v ON o.question_version_id=v.question_version_id "
          + "INNER JOIN sts_question_question q ON v.question_id=q.question_id WHERE q.question_code=?",
        uid + "-Q");
      jdbcTemplate.update(
        "DELETE v FROM sts_question_version v INNER JOIN sts_question_question q ON v.question_id = q.question_id "
          + "WHERE q.question_code = ?", uid + "-Q");
      jdbcTemplate.update("DELETE FROM sts_question_question WHERE question_code = ?", uid + "-Q");
      jdbcTemplate.update("DELETE FROM sts_question_bank WHERE bank_code = ?", uid + "-QB");
      jdbcTemplate.update(
        "DELETE v FROM sts_paper_version v INNER JOIN sts_paper_paper p ON v.paper_id = p.paper_id "
          + "WHERE p.paper_code = ?", uid + "-P");
      jdbcTemplate.update("DELETE FROM sts_paper_paper WHERE paper_code = ?", uid + "-P");
      jdbcTemplate.update(
        "DELETE v FROM sts_course_version v INNER JOIN sts_course_course c ON v.course_id = c.course_id "
          + "WHERE c.course_code LIKE ?", uid + "-C%");
      jdbcTemplate.update(
        "DELETE ch FROM sts_course_chapter ch INNER JOIN sts_course_course c ON ch.course_id = c.course_id "
          + "WHERE c.course_code LIKE ?", uid + "-C%");
      jdbcTemplate.update("DELETE FROM sts_course_course WHERE course_code LIKE ?", uid + "-C%");
      jdbcTemplate.update(
        "DELETE r FROM sts_training_standard_rule r INNER JOIN sts_training_standard s "
          + "ON r.training_standard_id = s.training_standard_id WHERE s.standard_code = ?", uid + "-ST");
      jdbcTemplate.update("DELETE FROM sts_training_standard WHERE standard_code = ?", uid + "-ST");
      jdbcTemplate.update("DELETE FROM sts_training_type WHERE type_code = ?", uid + "-TY");
      jdbcTemplate.update("DELETE FROM us_sys_user WHERE user_code LIKE ?", uid + "-U%");
    }
  }

  @Test
  @DisplayName("PUBLISHED 直接 end 返回 409/8163 且零副作用")
  void publishedPlan_end_returns8163WithoutSideEffects() {
    SeededPlan seeded = seedPublishedMixedPlan();
    TrainingParticipant before = participantMapper.selectById(seeded.participantId());

    assertThatThrownBy(() -> lifecycleService.end(seeded.planId(), seeded.plan().getVersion()))
      .isInstanceOfSatisfying(BusinessException.class, ex -> {
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getCode()).isEqualTo(8163);
      });

    TrainingPlan after = planMapper.selectById(seeded.planId());
    assertThat(after.getPublishStatus()).isEqualTo("PUBLISHED");
    assertThat(after.getEndedTime()).isNull();
    TrainingParticipant participant = participantMapper.selectById(seeded.participantId());
    assertThat(participant.getFinalResultSnapshotJson()).isEqualTo(before.getFinalResultSnapshotJson());
    assertThat(participant.getResultFinalizedTime()).isNull();
    assertThat(resultMapper.selectCount(new LambdaQueryWrapper<TrainingParticipantContentResult>()
                                          .eq(TrainingParticipantContentResult::getTrainingPlanId, seeded.planId())
                                          .isNotNull(TrainingParticipantContentResult::getFinalizedTime)))
      .isZero();
  }

  @Test
  @DisplayName("发布→四类执行→start→正式事实→同步→end→v3 快照不可变")
  void mixedPlan_publishGenerateStartSyncEnd_freezesV3Snapshot() {
    SeededPlan seeded = seedPublishedMixedPlan();
    TrainingPlan published = planMapper.selectById(seeded.planId());
    assertThat(published.getPublishStatus()).isEqualTo("PUBLISHED");
    assertThat(published.getSourceSnapshotHash()).isNotBlank();
    for (TrainingPlanContent content : listContents(seeded.planId())) {
      assertThat(content.getResourceSnapshotJson()).isNotBlank();
      assertThat(content.getResourceSnapshotHash()).isNotBlank();
    }
    TrainingParticipantAssignRequest assign = new TrainingParticipantAssignRequest();
    assign.setUserId(seeded.teacherUserId());
    assertThatThrownBy(() -> planService.assignParticipant(seeded.planId(), assign))
      .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getCode()).isEqualTo(8109));

    TrainingGenerateExecutionsResultVO first = executionService.generateExecutions(seeded.planId());
    assertThat(first.getFailedCount()).as(failureText(first)).isZero();
    assertThat(first.getExecutionLinksCreated()).isEqualTo(3);
    assertThat(first.getOfflineSessionsCreated()).isEqualTo(1);

    TrainingGenerateExecutionsResultVO retry = executionService.generateExecutions(seeded.planId());
    assertThat(retry.getFailedCount()).isZero();
    assertThat(retry.getSkippedExisting()).isEqualTo(4);
    assertThat(retry.getExecutionLinksCreated()).isZero();
    assertThat(retry.getOfflineSessionsCreated()).isZero();

    TrainingExecutionLink courseLink = link(seeded.courseContentId(), "COURSE");
    TrainingExecutionLink examLink = link(seeded.examContentId(), "EXAM");
    TrainingExecutionLink bankLink = link(seeded.bankContentId(), "QUESTION_BANK");
    TrainingOfflineSession session = offlineSessionMapper.selectOne(
      new LambdaQueryWrapper<TrainingOfflineSession>()
        .eq(TrainingOfflineSession::getPlanContentId, seeded.offlineContentId()));
    CourseOffering courseOffering = offeringMapper.selectOne(new LambdaQueryWrapper<CourseOffering>()
                                                               .eq(CourseOffering::getPlanContentId,
                                                                   seeded.courseContentId()));
    assertThat(courseOffering.getPublishStatus()).isEqualTo("PUBLISHED");
    assertThat(courseLink.getExecutionId()).isEqualTo(courseOffering.getCourseOfferingId());
    AssessmentExecution examExecution = assessmentExecutionMapper.selectById(examLink.getExecutionId());
    AssessmentExecution bankExecution = assessmentExecutionMapper.selectById(bankLink.getExecutionId());
    assertThat(examExecution.getSourceType()).isEqualTo("TRAINING_EXAM");
    assertThat(bankExecution.getSourceType()).isEqualTo("TRAINING_PRACTICE");
    Exam planExam = examMapper.selectById(examExecution.getExamId());
    assertThat(planExam.getPlanContentId()).isEqualTo(seeded.examContentId());
    assertThat(planExam.getPublishStatus()).isEqualTo("PUBLISHED");
    Exam bankExam = examMapper.selectById(bankExecution.getExamId());
    assertThat(bankExam.getPublishStatus()).isEqualTo("PUBLISHED");
    assertThat(session.getOfflineSessionId()).isNotNull();
    assertThat(examCandidateMapper.selectCount(new LambdaQueryWrapper<ExamCandidate>()
                                                 .eq(ExamCandidate::getTrainingParticipantId, seeded.participantId())
                                                 .eq(ExamCandidate::getAssignmentSource, "TRAINING")))
      .isEqualTo(2);

    lifecycleService.start(seeded.planId(), published.getVersion());
    assertThat(planMapper.selectById(seeded.planId()).getPublishStatus()).isEqualTo("RUNNING");

    writeOfficialFacts(seeded, courseLink, examLink, bankLink, session);
    resultSyncService.synchronize(seeded.planId());
    List<TrainingParticipantContentResult> rows = resultMapper.selectList(
      new LambdaQueryWrapper<TrainingParticipantContentResult>()
        .eq(TrainingParticipantContentResult::getTrainingParticipantId, seeded.participantId())
        .orderByAsc(TrainingParticipantContentResult::getPlanContentId));
    assertThat(rows).hasSize(4);
    assertThat(rows).extracting(TrainingParticipantContentResult::getResultStatus)
      .containsOnly("COMPLETED");
    assertThat(rows).extracting(TrainingParticipantContentResult::getPlanContentId).doesNotHaveDuplicates();
    TrainingParticipantContentResult examRow = rows.stream()
      .filter(row -> seeded.examContentId().equals(row.getPlanContentId())).findFirst().orElseThrow();
    assertThat(examRow.getSourceStatus()).isEqualTo("PASSED");
    assertThat(examRow.getResultStatus()).isEqualTo("COMPLETED");
    assertThat(examRow.getEffectiveDurationSeconds()).isZero();
    TrainingParticipant synced = participantMapper.selectById(seeded.participantId());
    assertThat(synced.getOnlineHours()).isEqualByComparingTo("1.00");

    TrainingPlan running = planMapper.selectById(seeded.planId());
    lifecycleService.end(seeded.planId(), running.getVersion());
    TrainingPlan ended = planMapper.selectById(seeded.planId());
    assertThat(ended.getPublishStatus()).isEqualTo("ENDED");
    assertThat(ended.getEndedTime()).isNotNull();

    TrainingParticipant frozen = participantMapper.selectById(seeded.participantId());
    assertThat(frozen.getResultStatus()).isEqualTo("PASSED");
    assertThat(frozen.getResultFinalizedTime()).isNotNull();
    assertThat(frozen.getFinalResultSnapshotJson()).contains("\"schemaVersion\":3");
    assertThat(frozen.getFinalResultSnapshotJson()).contains("\"sourceStatus\"");
    assertThat(frozen.getFinalResultSnapshotHash()).isEqualTo(sha256Hex(frozen.getFinalResultSnapshotJson()));
    List<TrainingParticipantContentResult> finalized = resultMapper.selectList(
      new LambdaQueryWrapper<TrainingParticipantContentResult>()
        .eq(TrainingParticipantContentResult::getTrainingParticipantId, seeded.participantId()));
    assertThat(finalized).allMatch(row -> frozen.getResultFinalizedTime().equals(row.getFinalizedTime()));

    String snapshot = frozen.getFinalResultSnapshotJson();
    String hash = frozen.getFinalResultSnapshotHash();
    resultSyncService.synchronize(seeded.planId());
    TrainingParticipant afterSync = participantMapper.selectById(seeded.participantId());
    assertThat(afterSync.getFinalResultSnapshotJson()).isEqualTo(snapshot);
    assertThat(afterSync.getFinalResultSnapshotHash()).isEqualTo(hash);
  }

  private SeededPlan seedPublishedMixedPlan() {
    uid = "MX6" + Long.toHexString(System.nanoTime());
    Long orgId = organizationMapper.selectByOrgCode("DEMO").getOrganizationId();
    Long learnerId = insertUser(orgId, uid + "-UL", "学员");
    Long teacherId = insertUser(orgId, uid + "-UT", "教师");
    Long typeId = insertType(orgId);
    Long standardId = insertStandard(orgId, typeId);
    Long courseId = CoursePublishItHelper.seedCourseWithChapter(orgId, courseMapper, chapterMapper, uid + "-C");
    CoursePublishItHelper.publishCourse(courseId, coursePublishService, courseMapper);
    Course course = courseMapper.selectById(courseId);
    Long paperVersionId = insertPublishedPaper(orgId);
    Long examId = insertPublishedExam(orgId, paperVersionId);
    Long bankId = insertQuestionBank(orgId);

    TrainingPlan plan = new TrainingPlan();
    plan.setOrganizationId(orgId);
    plan.setPlanCode(uid + "-PL");
    plan.setPlanName("混合计划");
    plan.setTrainingTypeId(typeId);
    plan.setTrainingNature("INITIAL");
    plan.setTrainingStandardId(standardId);
    plan.setStartTime(LocalDateTime.of(2026, 8, 31, 8, 0));
    plan.setEndTime(LocalDateTime.of(2026, 9, 30, 18, 0));
    plan.setLocation("园区");
    plan.setPublishStatus("DRAFT");
    plan.setStatus(1);
    planMapper.insert(plan);
    planId = plan.getTrainingPlanId();

    TrainingPlanStage stage = new TrainingPlanStage();
    stage.setTrainingPlanId(planId);
    stage.setStageName("阶段一");
    stage.setStageType("MIXED");
    stage.setStartTime(plan.getStartTime());
    stage.setEndTime(plan.getEndTime());
    stage.setSortOrder(1);
    stage.setStageStatus("PENDING");
    stageMapper.insert(stage);

    TrainingPlanContent courseContent = insertContent(stage.getPlanStageId(), "COURSE", course.getCourseId(),
                                                      "安全课程", new BigDecimal("4"), courseConfig(), null);
    TrainingPlanContent examContent = insertContent(stage.getPlanStageId(), "EXAM", examId,
                                                    "理论考试", new BigDecimal("2"), null, "FORMAL_EXAM");
    TrainingPlanContent bankContent = insertContent(stage.getPlanStageId(), "QUESTION_BANK", bankId,
                                                    "题库练习", new BigDecimal("2"), bankConfig(), null);
    TrainingPlanContent offlineContent = insertContent(stage.getPlanStageId(), "OFFLINE", null,
                                                       "线下实操", new BigDecimal("4"), offlineConfig(teacherId), null);

    TrainingParticipant participant = new TrainingParticipant();
    participant.setOrganizationId(orgId);
    participant.setTrainingPlanId(planId);
    participant.setUserId(learnerId);
    participant.setParticipantOrgId(orgId);
    participant.setJoinSource("ASSIGN");
    participant.setJoinedTime(LocalDateTime.now());
    participant.setParticipantStatus("PENDING");
    participant.setOnlineHours(BigDecimal.ZERO);
    participant.setOfflineHours(BigDecimal.ZERO);
    participantMapper.insert(participant);

    // 生命周期夹具使用明确已批准的版本；审批流程由专用事务IT验证。
    jdbcTemplate.update("INSERT INTO sts_training_plan_approval(organization_id,training_plan_id,plan_version,submitter_user_id,approver_user_id,approval_status) VALUES(?,?,?,?,?,?)", orgId,planId,plan.getVersion(),learnerId,teacherId,"APPROVED");
    planService.publish(planId, plan.getVersion());
    return new SeededPlan(planMapper.selectById(planId), participant.getTrainingParticipantId(), learnerId, teacherId,
                          courseContent.getPlanContentId(), examContent.getPlanContentId(),
                          bankContent.getPlanContentId(), offlineContent.getPlanContentId());
  }

  private void writeOfficialFacts(SeededPlan seeded, TrainingExecutionLink courseLink,
                                  TrainingExecutionLink examLink, TrainingExecutionLink bankLink,
                                  TrainingOfflineSession session) {
    CourseEnrollment enrollment = enrollmentMapper.selectOne(
      new LambdaQueryWrapper<CourseEnrollment>()
        .eq(CourseEnrollment::getCourseOfferingId, courseLink.getExecutionId())
        .eq(CourseEnrollment::getTrainingParticipantId, seeded.participantId()));
    enrollment.setLearningStatus("COMPLETED");
    enrollment.setProgressPercent(new BigDecimal("100.00"));
    enrollment.setTotalStudySeconds(3600);
    enrollment.setFinalScore(new BigDecimal("88.00"));
    enrollment.setCompletedTime(LocalDateTime.of(2026, 8, 31, 12, 0));
    enrollmentMapper.updateById(enrollment);

    publishCandidate(examLink.getExecutionId(), seeded.participantId(), new BigDecimal("90.00"));
    publishCandidate(bankLink.getExecutionId(), seeded.participantId(), new BigDecimal("80.00"));

    TrainingOfflineScore score = new TrainingOfflineScore();
    score.setTrainingParticipantId(seeded.participantId());
    score.setOfflineSessionId(session.getOfflineSessionId());
    score.setScoreName("实操");
    score.setScore(new BigDecimal("85.00"));
    score.setMaxScore(new BigDecimal("100.00"));
    score.setScoreSource("MANUAL");
    score.setScoredBy(seeded.teacherUserId());
    score.setScoredTime(session.getEndTime());
    offlineScoreMapper.insert(score);
  }

  private void publishCandidate(Long executionId, Long participantId, BigDecimal score) {
    ExamCandidate candidate = examCandidateMapper.selectOne(
      new LambdaQueryWrapper<ExamCandidate>()
        .eq(ExamCandidate::getAssessmentExecutionId, executionId)
        .eq(ExamCandidate::getTrainingParticipantId, participantId));
    candidate.setCandidateStatus("PASSED");
    candidate.setFinalScore(score);
    candidate.setBestScore(score);
    candidate.setResultPublishedTime(LocalDateTime.of(2026, 8, 31, 13, 0));
    examCandidateMapper.updateById(candidate);
  }

  private TrainingPlanContent insertContent(Long stageId, String type, Long resourceId, String name,
                                            BigDecimal hours, TrainingContentExecutionConfig config,
                                            String assessmentMode) {
    TrainingPlanContent content = new TrainingPlanContent();
    content.setPlanStageId(stageId);
    content.setContentType(type);
    content.setResourceId(resourceId);
    content.setAssessmentMode(assessmentMode);
    content.setExecutionConfigJson(configCodec.toJson(config));
    content.setContentName(name);
    content.setIsRequired(1);
    content.setIsScored(1);
    content.setRequiredHours(hours);
    content.setSortOrder(switch (type) {
      case "COURSE" -> 1;
      case "EXAM" -> 2;
      case "QUESTION_BANK" -> 3;
      default -> 4;
    });
    content.setContentStatus("PENDING");
    contentMapper.insert(content);
    return content;
  }

  private TrainingContentExecutionConfig courseConfig() {
    TrainingContentExecutionConfig config = new TrainingContentExecutionConfig();
    config.setCompletionRule("ALL_REQUIRED_ITEMS");
    return config;
  }

  private TrainingContentExecutionConfig bankConfig() {
    TrainingContentExecutionConfig config = new TrainingContentExecutionConfig();
    config.setDurationSeconds(1800);
    config.setPassScore(new BigDecimal("60"));
    config.setMaxAttempts(1);
    TrainingQuestionBankDrawRuleRequest rule = new TrainingQuestionBankDrawRuleRequest();
    rule.setQuestionType("SINGLE");
    rule.setDrawCount(1);
    rule.setScorePerQuestion(new BigDecimal("100"));
    rule.setSortOrder(1);
    config.setDrawRules(List.of(rule));
    return config;
  }

  private TrainingContentExecutionConfig offlineConfig(Long teacherUserId) {
    TrainingContentExecutionConfig config = new TrainingContentExecutionConfig();
    config.setTeacherUserId(teacherUserId);
    config.setLocation("实训车间");
    config.setStartTime(LocalDateTime.of(2026, 8, 31, 9, 0));
    config.setEndTime(LocalDateTime.of(2026, 8, 31, 17, 0));
    config.setPassScore(new BigDecimal("60"));
    return config;
  }

  private Long insertUser(Long orgId, String code, String name) {
    SysUser user = new SysUser();
    user.setOrganizationId(orgId);
    user.setUserCode(code);
    user.setUserName(name);
    user.setMemberType("INTERNAL");
    user.setStatus(1);
    user.setDeptId(1L);
    sysUserMapper.insert(user);
    return user.getUserId();
  }

  private Long insertType(Long orgId) {
    TrainingType type = new TrainingType();
    type.setOrganizationId(orgId);
    type.setTypeCode(uid + "-TY");
    type.setTypeName("混合验收类型");
    type.setInitialHours(new BigDecimal("8"));
    type.setReviewHours(new BigDecimal("4"));
    type.setOnlineMaxRatio(new BigDecimal("1.0000"));
    type.setStatus(1);
    type.setDescription("IT");
    typeMapper.insert(type);
    return type.getTrainingTypeId();
  }

  private Long insertStandard(Long orgId, Long typeId) {
    TrainingStandard standard = new TrainingStandard();
    standard.setOrganizationId(orgId);
    standard.setStandardCode(uid + "-ST");
    standard.setStandardName("混合验收标准");
    standard.setTrainingTypeId(typeId);
    standard.setStandardVersion("1");
    standard.setPublishStatus("PUBLISHED");
    standard.setStatus(1);
    standardMapper.insert(standard);
    TrainingStandardRule rule = new TrainingStandardRule();
    rule.setTrainingStandardId(standard.getTrainingStandardId());
    rule.setRuleType("TOTAL_HOURS");
    rule.setRequiredValue(new BigDecimal("8"));
    rule.setUnit("HOUR");
    rule.setSortOrder(1);
    rule.setStatus(1);
    rule.setDescription("计划内容要求学时");
    standardRuleMapper.insert(rule);
    return standard.getTrainingStandardId();
  }

  private Long insertPublishedPaper(Long orgId) {
    Paper paper = new Paper();
    paper.setOrganizationId(orgId);
    paper.setPaperCode(uid + "-P");
    paper.setPaperName("理论卷");
    paper.setPaperType("FIXED");
    paper.setCurrentVersionNo(1);
    paper.setArchiveStatus("ACTIVE");
    paper.setStatus(1);
    paperMapper.insert(paper);
    PaperVersion version = new PaperVersion();
    version.setPaperId(paper.getPaperId());
    version.setVersionNo(1);
    version.setDurationSeconds(3600);
    version.setTotalScore(new BigDecimal("100.00"));
    version.setPassRatio(new BigDecimal("0.6000"));
    version.setPassScore(new BigDecimal("60.00"));
    version.setQuestionCount(1);
    version.setQuestionOrderMode("IMPORT");
    version.setMultiSelectScoring("ALL_OR_NOTHING");
    version.setDrawSettingMode("UNIFIED");
    version.setPublishedStatus("PUBLISHED");
    version.setPublishedBy(1L);
    version.setPublishedTime(LocalDateTime.now());
    paperVersionMapper.insert(version);
    return version.getPaperVersionId();
  }

  private Long insertPublishedExam(Long orgId, Long paperVersionId) {
    Exam exam = new Exam();
    exam.setOrganizationId(orgId);
    exam.setExamCode(uid + "-EX");
    exam.setExamName("来源理论考试");
    exam.setExamType("ONLINE");
    exam.setPaperVersionId(paperVersionId);
    exam.setDurationSeconds(3600);
    exam.setPassRatio(new BigDecimal("0.6000"));
    exam.setPassScore(new BigDecimal("60.00"));
    exam.setCredit(BigDecimal.ZERO);
    exam.setQuestionOrderMode("IMPORT");
    exam.setMultiSelectScoring("ALL_OR_NOTHING");
    exam.setIsNoticeEnabled(0);
    exam.setPublishStatus("PUBLISHED");
    exam.setPublishedTime(LocalDateTime.now());
    exam.setStatus(1);
    examMapper.insert(exam);
    ExamPolicy policy = new ExamPolicy();
    policy.setExamId(exam.getExamId());
    policy.setIsWatermarkEnabled(0);
    policy.setIsPcAllowed(1);
    policy.setIsAppAllowed(1);
    policy.setIsH5Allowed(1);
    policy.setIsSplitScreenForceSubmit(0);
    policy.setIsCopyRefreshDevtoolDisabled(0);
    policy.setIsRetakeEnabled(0);
    policy.setRetakeMaxCount(0);
    policy.setRetakeScoreMode("HIGHEST");
    policy.setIsRetakeRecordVisible(0);
    policy.setPassPoint(BigDecimal.ZERO);
    policy.setFailPoint(BigDecimal.ZERO);
    policy.setAbsentPoint(BigDecimal.ZERO);
    policy.setLateForbidSeconds(0);
    policy.setAnswerDetailMode("SUBMITTED");
    policy.setIsAnalysisVisible(1);
    policy.setIsScoreVisible(1);
    policy.setIsRankingEnabled(0);
    examPolicyMapper.insert(policy);
    return exam.getExamId();
  }

  private Long insertQuestionBank(Long orgId) {
    QuestionBank bank = new QuestionBank();
    bank.setOrganizationId(orgId);
    bank.setBankCode(uid + "-QB");
    bank.setBankName("练习题库");
    bank.setDifficulty("NORMAL");
    bank.setQuestionOrderMode("IMPORT");
    bank.setArchiveStatus("ACTIVE");
    bank.setStatus(1);
    questionBankMapper.insert(bank);
    Question question = new Question();
    question.setOrganizationId(orgId);
    question.setQuestionBankId(bank.getQuestionBankId());
    question.setQuestionCode(uid + "-Q");
    question.setCurrentVersionNo(1);
    question.setArchiveStatus("ACTIVE");
    question.setStatus(1);
    questionMapper.insert(question);
    QuestionVersion version = new QuestionVersion();
    version.setQuestionId(question.getQuestionId());
    version.setVersionNo(1);
    version.setQuestionType("SINGLE");
    version.setStem("安全标志颜色？");
    version.setDifficulty("NORMAL");
    version.setGradingMode("AUTO");
    version.setIsUnorderedAnswerAllowed(0);
    version.setIsCaseIgnored(0);
    version.setIsFullHalfWidthIgnored(0);
    version.setPublishedStatus("PUBLISHED");
    version.setPublishedBy(1L);
    version.setPublishedTime(LocalDateTime.now());
    questionVersionMapper.insert(version);
    QuestionOption option = new QuestionOption();
    option.setQuestionVersionId(version.getQuestionVersionId());
    option.setOptionKey("A");
    option.setOptionContent("红色");
    option.setIsCorrect(1);
    option.setSortOrder(1);
    questionOptionMapper.insert(option);
    return bank.getQuestionBankId();
  }

  private List<TrainingPlanContent> listContents(Long trainingPlanId) {
    List<Long> stageIds = stageMapper.selectList(new LambdaQueryWrapper<TrainingPlanStage>()
                                                   .eq(TrainingPlanStage::getTrainingPlanId, trainingPlanId))
      .stream().map(TrainingPlanStage::getPlanStageId).toList();
    return contentMapper.selectList(new LambdaQueryWrapper<TrainingPlanContent>()
                                      .in(TrainingPlanContent::getPlanStageId, stageIds)
                                      .orderByAsc(TrainingPlanContent::getSortOrder));
  }

  private TrainingExecutionLink link(Long contentId, String type) {
    return executionLinkMapper.selectOne(new LambdaQueryWrapper<TrainingExecutionLink>()
                                           .eq(TrainingExecutionLink::getPlanContentId, contentId)
                                           .eq(TrainingExecutionLink::getExecutionType, type));
  }

  private String failureText(TrainingGenerateExecutionsResultVO result) {
    return result.getFailures().stream()
      .map(item -> item.getContentType() + ":" + item.getCode() + ":" + item.getMessage())
      .reduce((a, b) -> a + "; " + b)
      .orElse("no-failure");
  }

  private static String sha256Hex(String json) {
    try {
      return HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }

  private record SeededPlan(TrainingPlan plan, Long participantId, Long learnerUserId, Long teacherUserId,
                            Long courseContentId, Long examContentId, Long bankContentId, Long offlineContentId) {
    Long planId() {
      return plan.getTrainingPlanId();
    }
  }
}
