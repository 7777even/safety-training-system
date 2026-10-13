package com.sts.training.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.admin.entity.SysUser;
import com.sts.admin.mapper.SysUserMapper;
import com.sts.common.exception.BusinessException;
import com.sts.course.entity.Course;
import com.sts.course.mapper.CourseMapper;
import com.sts.course.mapper.CourseVersionMapper;
import com.sts.evaluation.mapper.EvaluationFormMapper;
import com.sts.evaluation.mapper.EvaluationFormVersionMapper;
import com.sts.exam.entity.AssessmentExecution;
import com.sts.exam.entity.Exam;
import com.sts.exam.entity.ExamPolicy;
import com.sts.exam.entity.ExamReviewer;
import com.sts.exam.mapper.ExamMapper;
import com.sts.exam.mapper.ExamPolicyMapper;
import com.sts.exam.mapper.ExamReviewerMapper;
import com.sts.exam.service.AssessmentExecutionService;
import com.sts.exam.service.AssessmentReviewerService;
import com.sts.question.draw.QuestionDrawEngine;
import com.sts.question.entity.QuestionBank;
import com.sts.question.mapper.QuestionBankMapper;
import com.sts.question.service.QuestionCandidatePoolService;
import com.sts.training.entity.*;
import com.sts.training.mapper.*;
import com.sts.training.vo.TrainingPublishIssueVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrainingPlanPublishValidatorTest {

  private TrainingPlanStageMapper stageMapper;
  private TrainingPlanContentMapper contentMapper;
  private TrainingParticipantMapper participantMapper;
  private ExamMapper examMapper;
  private ExamPolicyMapper examPolicyMapper;
  private QuestionBankMapper questionBankMapper;
  private QuestionCandidatePoolService candidatePoolService;
  private AssessmentReviewerService assessmentReviewerService;
  private AssessmentExecutionService assessmentExecutionService;
  private ExamReviewerMapper examReviewerMapper;
  private SysUserMapper sysUserMapper;
  private CourseMapper courseMapper;
  private CourseVersionMapper courseVersionMapper;
  private TrainingResourceAccessHelper resourceAccessHelper;
  private TrainingStandardRuleMapper standardRuleMapper;
  private TrainingPlanPublishValidator validator;

  @BeforeEach
  void setUp() {
    stageMapper = mock(TrainingPlanStageMapper.class);
    contentMapper = mock(TrainingPlanContentMapper.class);
    participantMapper = mock(TrainingParticipantMapper.class);
    examMapper = mock(ExamMapper.class);
    examPolicyMapper = mock(ExamPolicyMapper.class);
    questionBankMapper = mock(QuestionBankMapper.class);
    candidatePoolService = mock(QuestionCandidatePoolService.class);
    assessmentReviewerService = mock(AssessmentReviewerService.class);
    assessmentExecutionService = mock(AssessmentExecutionService.class);
    examReviewerMapper = mock(ExamReviewerMapper.class);
    sysUserMapper = mock(SysUserMapper.class);
    courseMapper = mock(CourseMapper.class);
    courseVersionMapper = mock(CourseVersionMapper.class);
    resourceAccessHelper = mock(TrainingResourceAccessHelper.class);
    standardRuleMapper = mock(TrainingStandardRuleMapper.class);
    ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    validator = new TrainingPlanPublishValidator(
      stageMapper, contentMapper, participantMapper,
      mock(TrainingPlanEvaluationFormMapper.class), standardRuleMapper,
      resourceAccessHelper, mock(EvaluationFormVersionMapper.class), mock(EvaluationFormMapper.class),
      mock(com.sts.evaluation.mapper.EvaluationScoreRuleMapper.class),
      courseMapper, courseVersionMapper, examMapper, examPolicyMapper,
      questionBankMapper, candidatePoolService, mock(TrainingQuestionPoolSnapshotService.class),
      assessmentReviewerService, assessmentExecutionService,
      examReviewerMapper, sysUserMapper, new TrainingContentConfigCodec(objectMapper), objectMapper);
    when(resourceAccessHelper.requireTypeForUse(1L)).thenReturn(type());
    when(resourceAccessHelper.requireStandardForUse(null)).thenReturn(null);
    when(resourceAccessHelper.requireTemplateForUse(null)).thenReturn(null);
    when(participantMapper.selectCount(any())).thenReturn(1L);
    TrainingPlanStage stage = new TrainingPlanStage();
    stage.setPlanStageId(200L);
    stage.setStageName("理论练习阶段");
    when(stageMapper.selectList(any())).thenReturn(List.of(stage));
  }

  @Test
  @DisplayName("矩阵显式旧版本发布时保持冻结，不升级为课程当前版本")
  void explicitCourseVersionRemainsFrozen() {
    Course course = new Course(); course.setCourseId(99L); course.setOrganizationId(1L);
    course.setStatus(1); course.setPublishStatus("PUBLISHED"); course.setCurrentVersionId(22L);
    when(courseMapper.selectById(99L)).thenReturn(course);
    var old = new com.sts.course.entity.CourseVersion(); old.setCourseVersionId(11L); old.setCourseId(99L); old.setVersionNo(1);
    when(courseVersionMapper.selectById(11L)).thenReturn(old);
    var content = new TrainingPlanContent(); content.setContentType("COURSE"); content.setResourceId(99L);
    content.setResourceVersionId(11L); content.setExecutionConfigJson("{\"completionRule\":\"ALL_REQUIRED_ITEMS\"}");
    org.springframework.test.util.ReflectionTestUtils.invokeMethod(validator, "freezeCourse", content);
    assertThat(content.getResourceVersionId()).isEqualTo(11L);
    assertThat(content.getResourceSnapshotJson()).contains("\"resourceVersionId\":11");
  }

  @Test
  @DisplayName("初培计划使用 initialHours 作为最低学时")
  void initialPlanUsesInitialHours() {
    TrainingPlanContent offline = completeOffline(new BigDecimal("30"));
    when(contentMapper.selectList(any())).thenReturn(List.of(offline));

    List<TrainingPublishIssueVO> issues = validator.collectIssues(plan());

    assertThat(issues).anySatisfy(issue -> {
      assertThat(issue.getCode()).isEqualTo(TrainingPlanPublishValidator.CODE_HOURS);
      assertThat(issue.getMessage()).contains("初培").contains("30").contains("80");
    });
  }

  @Test
  @DisplayName("复审计划使用 reviewHours，30 学时满足 24 学时基线")
  void reviewPlanUsesReviewHours() {
    TrainingPlanContent offline = completeOffline(new BigDecimal("30"));
    when(contentMapper.selectList(any())).thenReturn(List.of(offline));
    TrainingPlan plan = plan();
    plan.setTrainingNature("REVIEW");

    List<TrainingPublishIssueVO> issues = validator.collectIssues(plan);

    assertThat(issues).extracting(TrainingPublishIssueVO::getCode)
      .doesNotContain(TrainingPlanPublishValidator.CODE_HOURS);
  }

  @Test
  @DisplayName("培训标准 TOTAL_HOURS 不能降低初培类型基线")
  void standardCannotLowerTypeBaseline() {
    TrainingPlanContent offline = completeOffline(new BigDecimal("70"));
    when(contentMapper.selectList(any())).thenReturn(List.of(offline));
    stubPublishedStandard(hourRule("TOTAL_HOURS", new BigDecimal("60")));

    List<TrainingPublishIssueVO> issues = validator.collectIssues(planWithStandard());

    assertThat(issues).anySatisfy(issue -> {
      assertThat(issue.getCode()).isEqualTo(TrainingPlanPublishValidator.CODE_HOURS);
      assertThat(issue.getMessage()).contains("80");
    });
  }

  @Test
  @DisplayName("历史未知培训性质不能用于新发布")
  void unspecifiedNatureCannotPublish() {
    TrainingPlanContent offline = completeOffline(new BigDecimal("100"));
    when(contentMapper.selectList(any())).thenReturn(List.of(offline));
    TrainingPlan plan = plan();
    plan.setTrainingNature("UNSPECIFIED");

    List<TrainingPublishIssueVO> issues = validator.collectIssues(plan);

    assertThat(issues).extracting(TrainingPublishIssueVO::getCode)
      .contains(TrainingPlanPublishValidator.CODE_TRAINING_NATURE);
  }

  @Test
  @DisplayName("绑定标准缺指定课程时预检返回 8156")
  void missingRequiredCourseReturns8156() {
    TrainingPlanContent offline = completeOffline(new BigDecimal("80"));
    when(contentMapper.selectList(any())).thenReturn(List.of(offline));
    stubPublishedStandard(resourceRule("COURSE", 99L));

    List<TrainingPublishIssueVO> issues = validator.collectIssues(planWithStandard());

    assertThat(issues).anySatisfy(issue -> {
      assertThat(issue.getCode()).isEqualTo(TrainingPlanPublishValidator.CODE_REQUIRED_RESOURCE);
      assertThat(issue.getMessage()).contains("指定");
    });
  }

  @Test
  @DisplayName("计划内容已覆盖指定线下资源时不因 8156 失败")
  void matchingRequiredOfflineDoesNotReturn8156() {
    TrainingPlanContent offline = completeOffline(new BigDecimal("80"));
    offline.setResourceId(77L);
    when(contentMapper.selectList(any())).thenReturn(List.of(offline));
    stubPublishedStandard(resourceRule("OFFLINE", 77L));

    List<TrainingPublishIssueVO> issues = validator.collectIssues(planWithStandard());

    assertThat(issues).extracting(TrainingPublishIssueVO::getCode)
      .doesNotContain(TrainingPlanPublishValidator.CODE_REQUIRED_RESOURCE);
  }

  @Test
  @DisplayName("未绑定标准的计划不产生 8156")
  void planWithoutStandardDoesNotReturn8156() {
    TrainingPlanContent offline = completeOffline(new BigDecimal("80"));
    when(contentMapper.selectList(any())).thenReturn(List.of(offline));

    List<TrainingPublishIssueVO> issues = validator.collectIssues(plan());

    assertThat(issues).extracting(TrainingPublishIssueVO::getCode)
      .doesNotContain(TrainingPlanPublishValidator.CODE_REQUIRED_RESOURCE);
  }

  @Test
  @DisplayName("EXAM 缺少 assessmentMode 时拒绝发布")
  void examMissingAssessmentMode() {
    TrainingPlanContent content = content("EXAM", 501L);
    content.setIsRequired(1);
    content.setRequiredHours(new BigDecimal("80"));
    when(contentMapper.selectList(any())).thenReturn(List.of(content));
    Exam exam = publishedExam(501L);
    when(examMapper.selectById(501L)).thenReturn(exam);

    List<TrainingPublishIssueVO> issues = validator.collectIssues(plan());
    assertThat(issues).extracting(TrainingPublishIssueVO::getCode)
      .contains(TrainingPlanPublishValidator.CODE_ASSESSMENT_MODE);
  }

  @Test
  @DisplayName("QUESTION_BANK 缺少练习配置时拒绝发布")
  void questionBankMissingConfig() {
    TrainingPlanContent content = content("QUESTION_BANK", 601L);
    content.setIsRequired(1);
    when(contentMapper.selectList(any())).thenReturn(List.of(content));
    when(questionBankMapper.selectById(601L)).thenReturn(activeBank(601L));

    List<TrainingPublishIssueVO> issues = validator.collectIssues(plan());
    assertThat(issues).extracting(TrainingPublishIssueVO::getCode)
      .contains(TrainingPlanPublishValidator.CODE_BANK_CONFIG);
  }

  @Test
  @DisplayName("题库含简答题但规则只抽单选时允许发布")
  void unselectedEssayDoesNotBlockQuestionBankPractice() {
    TrainingPlanContent content = content("QUESTION_BANK", 601L);
    content.setIsRequired(1);
    content.setExecutionConfigJson(
      "{\"durationSeconds\":1800,\"maxAttempts\":2,\"drawRules\":[{\"questionType\":\"SINGLE\",\"drawCount\":2}]}");
    when(contentMapper.selectList(any())).thenReturn(List.of(content));
    when(questionBankMapper.selectById(601L)).thenReturn(activeBank(601L));
    when(candidatePoolService.loadCandidates(601L)).thenReturn(List.of(
      candidate(1L, "SINGLE", "NORMAL", null, QuestionDrawEngine.EvaluationMode.AUTO),
      candidate(2L, "SINGLE", "NORMAL", null, QuestionDrawEngine.EvaluationMode.AUTO),
      candidate(3L, "ESSAY", "NORMAL", null, QuestionDrawEngine.EvaluationMode.RECORD_ONLY)));

    List<TrainingPublishIssueVO> issues = validator.collectIssues(plan());
    assertThat(issues).extracting(TrainingPublishIssueVO::getCode)
      .doesNotContain(TrainingPlanPublishValidator.CODE_EVALUATION_ANOMALY,
                      TrainingPlanPublishValidator.CODE_CANDIDATE_SHORTAGE,
                      TrainingPlanPublishValidator.CODE_BANK_CONFIG);
  }

  @Test
  @DisplayName("题库练习允许直接抽取简答题且不要求分值")
  void essayRuleIsRecordOnlyAndDoesNotRequireScore() {
    TrainingPlanContent content = content("QUESTION_BANK", 601L);
    content.setIsRequired(1);
    content.setExecutionConfigJson(
      "{\"durationSeconds\":1800,\"maxAttempts\":1,"
        + "\"drawRules\":[{\"questionType\":\"ESSAY\",\"drawCount\":1}]}");
    when(contentMapper.selectList(any())).thenReturn(List.of(content));
    when(questionBankMapper.selectById(601L)).thenReturn(activeBank(601L));
    when(candidatePoolService.loadCandidates(601L)).thenReturn(List.of(
      candidate(1L, "ESSAY", "NORMAL", null, QuestionDrawEngine.EvaluationMode.RECORD_ONLY)));

    List<TrainingPublishIssueVO> issues = validator.collectIssues(plan());
    assertThat(issues).extracting(TrainingPublishIssueVO::getCode)
      .doesNotContain(TrainingPlanPublishValidator.CODE_EVALUATION_ANOMALY,
                      TrainingPlanPublishValidator.CODE_CANDIDATE_SHORTAGE,
                      TrainingPlanPublishValidator.CODE_BANK_CONFIG);
  }

  @Test
  @DisplayName("多条规则共享候选并因全局去重不足时返回可定位的 8166")
  void duplicateRulesReturnLocatedCandidateShortage() {
    TrainingPlanContent content = content("QUESTION_BANK", 601L);
    content.setContentName("岗位安全练习");
    content.setExecutionConfigJson(
      "{\"durationSeconds\":1800,\"maxAttempts\":1,\"drawRules\":["
        + "{\"questionType\":\"SINGLE\",\"drawCount\":2},"
        + "{\"questionType\":\"SINGLE\",\"drawCount\":2}]}");
    when(contentMapper.selectList(any())).thenReturn(List.of(content));
    when(questionBankMapper.selectById(601L)).thenReturn(activeBank(601L));
    when(candidatePoolService.loadCandidates(601L)).thenReturn(List.of(
      candidate(1L, "SINGLE", "NORMAL", null, QuestionDrawEngine.EvaluationMode.AUTO),
      candidate(2L, "SINGLE", "NORMAL", null, QuestionDrawEngine.EvaluationMode.AUTO),
      candidate(3L, "SINGLE", "NORMAL", null, QuestionDrawEngine.EvaluationMode.AUTO)));

    List<TrainingPublishIssueVO> issues = validator.collectIssues(plan());

    assertThat(issues).filteredOn(issue -> issue.getCode() == TrainingPlanPublishValidator.CODE_CANDIDATE_SHORTAGE)
      .singleElement().satisfies(issue -> {
        assertThat(issue.getStageId()).isEqualTo(200L);
        assertThat(issue.getStageName()).isEqualTo("理论练习阶段");
        assertThat(issue.getContentName()).isEqualTo("岗位安全练习");
        assertThat(issue.getResourceId()).isEqualTo(601L);
        assertThat(issue.getResourceName()).isEqualTo("题库");
        assertThat(issue.getRuleIndex()).isEqualTo(2);
        assertThat(issue.getQuestionType()).isEqualTo("SINGLE");
        assertThat(issue.getRequiredCount()).isEqualTo(2);
        assertThat(issue.getAvailableCount()).isEqualTo(1);
        assertThat(issue.getSuggestion()).contains("增加").contains("调整抽题规则");
      });
  }

  @Test
  @DisplayName("难度和知识点筛选后的单规则候选不足返回筛选明细")
  void filteredSingleRuleReturnsCandidateShortageDetails() {
    TrainingPlanContent content = content("QUESTION_BANK", 601L);
    content.setExecutionConfigJson(
      "{\"durationSeconds\":1800,\"maxAttempts\":1,"
        + "\"drawRules\":[{\"questionType\":\"SINGLE\",\"difficulty\":\"EASY\","
        + "\"knowledgePointId\":9,\"drawCount\":2}]}");
    when(contentMapper.selectList(any())).thenReturn(List.of(content));
    when(questionBankMapper.selectById(601L)).thenReturn(activeBank(601L));
    when(candidatePoolService.loadCandidates(601L)).thenReturn(List.of(
      candidate(1L, "SINGLE", "EASY", 9L, QuestionDrawEngine.EvaluationMode.AUTO),
      candidate(2L, "SINGLE", "HARD", 9L, QuestionDrawEngine.EvaluationMode.AUTO),
      candidate(3L, "SINGLE", "EASY", 8L, QuestionDrawEngine.EvaluationMode.AUTO)));

    List<TrainingPublishIssueVO> issues = validator.collectIssues(plan());

    assertThat(issues).filteredOn(issue -> issue.getCode() == TrainingPlanPublishValidator.CODE_CANDIDATE_SHORTAGE)
      .singleElement().satisfies(issue -> {
        assertThat(issue.getRuleIndex()).isEqualTo(1);
        assertThat(issue.getDifficulty()).isEqualTo("EASY");
        assertThat(issue.getKnowledgePointId()).isEqualTo(9L);
        assertThat(issue.getRequiredCount()).isEqualTo(2);
        assertThat(issue.getAvailableCount()).isEqualTo(1);
      });
  }

  @Test
  @DisplayName("规则命中的判定资产异常候选返回 8127，未命中异常候选不阻塞")
  void unsupportedEvaluationCandidateOnlyBlocksMatchedRule() {
    TrainingPlanContent content = content("QUESTION_BANK", 601L);
    content.setExecutionConfigJson(
      "{\"durationSeconds\":1800,\"maxAttempts\":1,"
        + "\"drawRules\":[{\"questionType\":\"FILL\",\"difficulty\":\"EASY\",\"drawCount\":1}]}");
    when(contentMapper.selectList(any())).thenReturn(List.of(content));
    when(questionBankMapper.selectById(601L)).thenReturn(activeBank(601L));
    when(candidatePoolService.loadCandidates(601L)).thenReturn(List.of(
      candidate(1L, "FILL", "EASY", 9L, QuestionDrawEngine.EvaluationMode.UNSUPPORTED),
      candidate(2L, "FILL", "HARD", 9L, QuestionDrawEngine.EvaluationMode.AUTO)));

    List<TrainingPublishIssueVO> issues = validator.collectIssues(plan());

    assertThat(issues).filteredOn(issue -> issue.getCode() == TrainingPlanPublishValidator.CODE_EVALUATION_ANOMALY)
      .singleElement().satisfies(issue -> {
        assertThat(issue.getRuleIndex()).isEqualTo(1);
        assertThat(issue.getQuestionType()).isEqualTo("FILL");
        assertThat(issue.getDifficulty()).isEqualTo("EASY");
      });
  }

  @Test
  @DisplayName("OFFLINE 缺少教师、地点、时间或及格线时拒绝发布")
  void offlineMissingConfig() {
    TrainingPlanContent content = content("OFFLINE", null);
    content.setIsRequired(1);
    when(contentMapper.selectList(any())).thenReturn(List.of(content));

    List<TrainingPublishIssueVO> issues = validator.collectIssues(plan());
    assertThat(issues).anySatisfy(issue -> {
      assertThat(issue.getCode()).isEqualTo(TrainingPlanPublishValidator.CODE_OFFLINE);
      assertThat(issue.getMessage()).isEqualTo("线下内容必须配置教师、地点、时间和及格线");
    });
  }

  @Test
  @DisplayName("EXAM 含主观题且无本机构有效批阅人时拒绝发布")
  void examSubjectiveWithoutOrgReviewerRejected() {
    TrainingPlanContent content = content("EXAM", 501L);
    content.setIsRequired(1);
    content.setAssessmentMode("FORMAL_EXAM");
    when(contentMapper.selectList(any())).thenReturn(List.of(content));
    when(examMapper.selectById(501L)).thenReturn(publishedExam(501L));
    when(assessmentReviewerService.paperRequiresManualReview(88L)).thenReturn(true);
    when(assessmentExecutionService.findStandaloneByExamId(501L)).thenReturn(null);

    List<TrainingPublishIssueVO> issues = validator.collectIssues(plan());
    assertThat(issues).anySatisfy(issue -> {
      assertThat(issue.getCode()).isEqualTo(TrainingPlanPublishValidator.CODE_EXAM);
      assertThat(issue.getPlanContentId()).isEqualTo(300L);
      assertThat(issue.getMessage()).contains("批阅");
    });
  }

  @Test
  @DisplayName("EXAM 冻结 schemaVersion=2 快照，含完整策略和批阅人")
  void examFreezesPaperVersion() {
    TrainingPlanContent content = content("EXAM", 501L);
    content.setIsRequired(1);
    content.setRequiredHours(new BigDecimal("2"));
    content.setAssessmentMode("FORMAL_EXAM");
    TrainingPlanContent offline = completeOffline(new BigDecimal("80"));
    offline.setPlanContentId(301L);
    when(contentMapper.selectList(any())).thenReturn(List.of(content, offline));
    when(contentMapper.updateById(any(TrainingPlanContent.class))).thenReturn(1);
    Exam exam = publishedExam(501L);
    exam.setExamCategoryId(3L);
    exam.setCoverFileId(9L);
    exam.setResponsibleUserId(7L);
    exam.setIsNoticeEnabled(1);
    exam.setNoticeContent("开考通知");
    exam.setCredit(new java.math.BigDecimal("1.00"));
    when(examMapper.selectById(501L)).thenReturn(exam);
    ExamPolicy policy = new ExamPolicy();
    policy.setIsPcAllowed(1);
    policy.setIsAppAllowed(1);
    policy.setIsH5Allowed(0);
    policy.setIsRetakeEnabled(0);
    policy.setRetakeMaxCount(0);
    policy.setAnswerDetailMode("SUBMITTED");
    policy.setIsAnalysisVisible(1);
    policy.setIsScoreVisible(1);
    policy.setIsRankingEnabled(0);
    when(examPolicyMapper.selectOne(any())).thenReturn(policy);
    when(assessmentReviewerService.paperRequiresManualReview(88L)).thenReturn(true);
    AssessmentExecution execution = new AssessmentExecution();
    execution.setAssessmentExecutionId(9001L);
    when(assessmentExecutionService.findStandaloneByExamId(501L)).thenReturn(execution);
    ExamReviewer reviewer = new ExamReviewer();
    reviewer.setReviewerUserId(7L);
    reviewer.setAssignmentSource("USER");
    when(examReviewerMapper.selectList(any())).thenReturn(List.of(reviewer));
    SysUser user = new SysUser();
    user.setUserId(7L);
    user.setOrganizationId(1L);
    user.setStatus(1);
    when(sysUserMapper.selectById(7L)).thenReturn(user);

    validator.freezeOrThrow(plan());
    assertThat(content.getRequiredHours()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(content.getResourceVersionId()).isEqualTo(88L);
    assertThat(content.getResourceSnapshotHash()).isNotBlank();
    assertThat(content.getResourceSnapshotJson()).contains("\"schemaVersion\":2");
    assertThat(content.getResourceSnapshotJson()).contains("FORMAL_EXAM");
    assertThat(content.getResourceSnapshotJson()).contains("\"reviewerUserId\":7");
    assertThat(content.getResourceSnapshotJson()).contains("\"requiresManualReview\":true");
    assertThat(content.getResourceSnapshotJson()).contains("\"isH5Allowed\":0");
  }

  @Test
  @DisplayName("计划 requiredHours 不足 TOTAL_HOURS 时拒绝发布")
  void totalHoursBelowStandard() {
    TrainingPlanContent offline = completeOffline(new BigDecimal("1"));
    when(contentMapper.selectList(any())).thenReturn(List.of(offline));
    stubPublishedStandard(hourRule("TOTAL_HOURS", new BigDecimal("8")));

    List<TrainingPublishIssueVO> issues = validator.collectIssues(planWithStandard());
    assertThat(issues).extracting(TrainingPublishIssueVO::getCode)
      .contains(TrainingPlanPublishValidator.CODE_HOURS);
  }

  @Test
  @DisplayName("计划线上占比超过 ONLINE_RATIO 时拒绝发布")
  void onlineRatioExceedsStandard() {
    TrainingPlanContent course = content("COURSE", 999L);
    course.setRequiredHours(new BigDecimal("8"));
    course.setExecutionConfigJson("{\"completionRule\":\"ALL_REQUIRED_ITEMS\"}");
    TrainingPlanContent offline = completeOffline(new BigDecimal("16"));
    offline.setPlanContentId(301L);
    when(contentMapper.selectList(any())).thenReturn(List.of(course, offline));
    Course published = new Course();
    published.setCourseId(999L);
    published.setOrganizationId(1L);
    published.setPublishStatus("PUBLISHED");
    published.setStatus(1);
    published.setCurrentVersionId(10L);
    when(courseMapper.selectById(999L)).thenReturn(published);
    stubPublishedStandard(hourRule("ONLINE_RATIO", new BigDecimal("0.3000")));
    TrainingPlan plan = planWithStandard();
    plan.setTrainingNature("REVIEW");

    List<TrainingPublishIssueVO> issues = validator.collectIssues(plan);
    assertThat(issues).extracting(TrainingPublishIssueVO::getCode)
      .contains(TrainingPlanPublishValidator.CODE_HOURS);
  }

  @Test
  @DisplayName("考试学时不能凑满类型学时")
  void mixedExamHoursDoNotPadStandard() {
    TrainingPlanContent exam = content("EXAM", 501L);
    exam.setAssessmentMode("QUIZ");
    exam.setRequiredHours(new BigDecimal("20"));
    TrainingPlanContent offline = completeOffline(new BigDecimal("60"));
    offline.setPlanContentId(301L);
    when(contentMapper.selectList(any())).thenReturn(List.of(exam, offline));
    when(examMapper.selectById(501L)).thenReturn(publishedExam(501L));
    stubPublishedStandard(hourRule("TOTAL_HOURS", new BigDecimal("8")));

    List<TrainingPublishIssueVO> issues = validator.collectIssues(planWithStandard());
    assertThat(issues).extracting(TrainingPublishIssueVO::getCode)
      .contains(TrainingPlanPublishValidator.CODE_HOURS);
  }

  @Test
  @DisplayName("考试不计入线上占比")
  void examHoursDoNotInflateOnlineRatio() {
    TrainingPlanContent exam = content("EXAM", 501L);
    exam.setAssessmentMode("QUIZ");
    exam.setRequiredHours(new BigDecimal("20"));
    TrainingPlanContent offline = completeOffline(new BigDecimal("24"));
    offline.setPlanContentId(301L);
    when(contentMapper.selectList(any())).thenReturn(List.of(exam, offline));
    when(examMapper.selectById(501L)).thenReturn(publishedExam(501L));
    stubPublishedStandard(hourRule("ONLINE_RATIO", new BigDecimal("0.3000")));
    TrainingPlan plan = planWithStandard();
    plan.setTrainingNature("REVIEW");

    List<TrainingPublishIssueVO> issues = validator.collectIssues(plan);
    assertThat(issues).extracting(TrainingPublishIssueVO::getCode)
      .doesNotContain(TrainingPlanPublishValidator.CODE_HOURS);
  }

  @Test
  @DisplayName("freezeOrThrow 在存在阻塞项时抛出首个业务错误")
  void freezeOrThrowUsesFirstIssue() {
    TrainingPlanContent content = content("OFFLINE", null);
    content.setIsRequired(1);
    when(contentMapper.selectList(any())).thenReturn(List.of(content));
    assertThatThrownBy(() -> validator.freezeOrThrow(plan()))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", TrainingPlanPublishValidator.CODE_OFFLINE);
  }

  private TrainingPlan plan() {
    TrainingPlan plan = new TrainingPlan();
    plan.setTrainingPlanId(100L);
    plan.setOrganizationId(1L);
    plan.setTrainingTypeId(1L);
    plan.setTrainingNature("INITIAL");
    plan.setPublishStatus("DRAFT");
    return plan;
  }

  private TrainingPlan planWithStandard() {
    TrainingPlan plan = plan();
    plan.setTrainingStandardId(9L);
    return plan;
  }

  private void stubPublishedStandard(TrainingStandardRule... rules) {
    TrainingStandard standard = new TrainingStandard();
    standard.setTrainingStandardId(9L);
    standard.setPublishStatus("PUBLISHED");
    standard.setStatus(1);
    when(resourceAccessHelper.requireStandardForUse(9L)).thenReturn(standard);
    when(standardRuleMapper.selectList(any())).thenReturn(List.of(rules));
  }

  private TrainingStandardRule hourRule(String ruleType, BigDecimal requiredValue) {
    TrainingStandardRule rule = new TrainingStandardRule();
    rule.setRuleType(ruleType);
    rule.setRequiredValue(requiredValue);
    rule.setStatus(1);
    return rule;
  }

  private TrainingStandardRule resourceRule(String ruleType, Long resourceId) {
    TrainingStandardRule rule = new TrainingStandardRule();
    rule.setRuleType(ruleType);
    rule.setResourceId(resourceId);
    rule.setStatus(1);
    return rule;
  }

  private TrainingPlanContent completeOffline(BigDecimal requiredHours) {
    TrainingPlanContent content = content("OFFLINE", null);
    content.setRequiredHours(requiredHours);
    content.setExecutionConfigJson(
      "{\"teacherUserId\":1,\"location\":\"车间\",\"startTime\":\"2026-08-31T09:00:00\","
        + "\"endTime\":\"2026-08-31T17:00:00\",\"passScore\":60}");
    return content;
  }

  private TrainingType type() {
    TrainingType type = new TrainingType();
    type.setTrainingTypeId(1L);
    type.setTypeCode("T");
    type.setTypeName("类型");
    type.setInitialHours(new BigDecimal("80"));
    type.setReviewHours(new BigDecimal("24"));
    return type;
  }

  private TrainingPlanContent content(String type, Long resourceId) {
    TrainingPlanContent content = new TrainingPlanContent();
    content.setPlanContentId(300L);
    content.setPlanStageId(200L);
    content.setContentType(type);
    content.setResourceId(resourceId);
    content.setContentName(type);
    content.setIsRequired(1);
    return content;
  }

  private Exam publishedExam(Long examId) {
    Exam exam = new Exam();
    exam.setExamId(examId);
    exam.setOrganizationId(1L);
    exam.setExamCode("E-1");
    exam.setExamName("理论考试");
    exam.setPublishStatus("PUBLISHED");
    exam.setStatus(1);
    exam.setPaperVersionId(88L);
    exam.setDurationSeconds(3600);
    exam.setPassScore(new java.math.BigDecimal("60"));
    exam.setPassRatio(new java.math.BigDecimal("0.6"));
    exam.setQuestionOrderMode("IMPORT");
    exam.setMultiSelectScoring("ALL_OR_NOTHING");
    return exam;
  }

  private QuestionBank activeBank(Long bankId) {
    QuestionBank bank = new QuestionBank();
    bank.setQuestionBankId(bankId);
    bank.setOrganizationId(1L);
    bank.setBankCode("QB-1");
    bank.setBankName("题库");
    bank.setStatus(1);
    bank.setArchiveStatus("ACTIVE");
    return bank;
  }

  private QuestionDrawEngine.Candidate candidate(long questionId, String type, String difficulty,
                                                 Long pointId,
                                                 QuestionDrawEngine.EvaluationMode evaluationMode) {
    return new QuestionDrawEngine.Candidate(questionId, 100L + questionId, type, difficulty,
                                            pointId == null ? java.util.Set.of() : java.util.Set.of(pointId),
                                            evaluationMode);
  }
}
