package com.sts.training.support;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sts.admin.config.MybatisMetaObjectHandler;
import com.sts.admin.config.MybatisPlusConfig;
import com.sts.admin.mapper.SysDeptMapper;
import com.sts.admin.mapper.SysUserMapper;
import com.sts.certificate.service.CertificateAutoIssueService;
import com.sts.course.mapper.*;
import com.sts.course.service.CourseOfferingService;
import com.sts.course.service.CourseOfferingServiceImpl;
import com.sts.course.service.CourseSnapshotAssembler;
import com.sts.course.service.TrainingCourseExecutionAdapter;
import com.sts.course.support.CoursePublishServiceItConfig;
import com.sts.evaluation.mapper.EvaluationFormMapper;
import com.sts.evaluation.mapper.EvaluationFormVersionMapper;
import com.sts.evaluation.mapper.EvaluationScoreRuleMapper;
import com.sts.exam.mapper.*;
import com.sts.exam.service.*;
import com.sts.org.mapper.OrganizationMapper;
import com.sts.paper.mapper.PaperMapper;
import com.sts.paper.mapper.PaperVersionMapper;
import com.sts.paper.mapper.PaperVersionQuestionMapper;
import com.sts.person.service.PersonSelectionService;
import com.sts.regulation.service.PlanHoursWarningRefreshService;
import com.sts.question.mapper.QuestionBankMapper;
import com.sts.question.mapper.QuestionMapper;
import com.sts.question.mapper.QuestionVersionMapper;
import com.sts.question.service.QuestionCandidatePoolService;
import com.sts.question.service.QuestionEvaluationModeResolver;
import com.sts.training.mapper.*;
import com.sts.training.port.TrainingAssessmentExecutionPort;
import com.sts.training.port.TrainingCourseExecutionPort;
import com.sts.training.service.*;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import static org.mockito.Mockito.mock;

/**
 * 四类内容混合计划纵向 IT：真实 Mapper + 发布/生成/同步/生命周期 Service，不启动 Web/Redis/Nacos。
 */
@SpringBootConfiguration
@ImportAutoConfiguration({
  DataSourceAutoConfiguration.class,
  DataSourceTransactionManagerAutoConfiguration.class,
  JdbcTemplateAutoConfiguration.class,
  MybatisPlusAutoConfiguration.class,
  TransactionAutoConfiguration.class
})
@EnableTransactionManagement
@MapperScan({
  "com.sts.training.mapper",
  "com.sts.course.mapper",
  "com.sts.exam.mapper",
  "com.sts.paper.mapper",
  "com.sts.question.mapper",
  "com.sts.org.mapper",
  "com.sts.evaluation.mapper",
  "com.sts.admin.mapper",
  "com.sts.asset.mapper"
})
@Import({MybatisPlusConfig.class, MybatisMetaObjectHandler.class, CoursePublishServiceItConfig.class})
public class TrainingMixedPlanItConfig {

  @Bean
  ObjectMapper objectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    return mapper;
  }

  @Bean
  AssessmentExecutionSnapshotCodec assessmentExecutionSnapshotCodec(ObjectMapper objectMapper) {
    return new AssessmentExecutionSnapshotCodec(objectMapper);
  }

  @Bean
  TrainingContentConfigCodec trainingContentConfigCodec(ObjectMapper objectMapper) {
    return new TrainingContentConfigCodec(objectMapper);
  }

  @Bean
  TrainingResourceAccessHelper trainingResourceAccessHelper(
    TrainingTypeMapper trainingTypeMapper,
    TrainingStandardMapper trainingStandardMapper,
    TrainingTemplateMapper trainingTemplateMapper) {
    return new TrainingResourceAccessHelper(trainingTypeMapper, trainingStandardMapper, trainingTemplateMapper);
  }

  @Bean
  AssessmentReviewerService assessmentReviewerService(
    ExamReviewerMapper reviewerMapper,
    PaperVersionQuestionMapper paperQuestionMapper,
    QuestionVersionMapper questionVersionMapper) {
    return new AssessmentReviewerService(reviewerMapper, paperQuestionMapper, questionVersionMapper);
  }

  @Bean
  AssessmentExecutionService assessmentExecutionService(
    AssessmentExecutionMapper executionMapper,
    ExamPolicyMapper policyMapper,
    PaperVersionMapper paperVersionMapper,
    PaperMapper paperMapper,
    QuestionBankMapper questionBankMapper,
    ObjectMapper objectMapper,
    AssessmentExecutionSnapshotCodec snapshotCodec,
    ExamCandidateMapper candidateMapper,
    ExamReviewerMapper reviewerMapper,
    ExamAttemptMapper attemptMapper,
    AssessmentExecutionQuestionPoolMapper executionQuestionPoolMapper,
    CourseAssessmentResultMapper courseAssessmentResultMapper) {
    return new AssessmentExecutionServiceImpl(
      executionMapper, policyMapper, paperVersionMapper, paperMapper, questionBankMapper, objectMapper, snapshotCodec,
      candidateMapper, reviewerMapper, attemptMapper,
      executionQuestionPoolMapper, courseAssessmentResultMapper,
      new ExamCandidateDeleteHelper(), new AssessmentExecutionDeleteHelper(), org.mockito.Mockito.mock(com.sts.exam.service.CourseKnowledgeSnapshotService.class));
  }

  @Bean
  ExamAttemptService examAttemptService() {
    return mock(ExamAttemptService.class);
  }

  @Bean
  ExamCandidateProfileSnapshotAssembler examCandidateProfileSnapshotAssembler(
    SysUserMapper sysUserMapper,
    SysDeptMapper sysDeptMapper,
    OrganizationMapper organizationMapper,
    ObjectMapper objectMapper) {
    return new ExamCandidateProfileSnapshotAssembler(
      sysUserMapper, sysDeptMapper, organizationMapper, objectMapper);
  }

  @Bean
  @Primary
  CourseOfferingService trainingAwareCourseOfferingService(
    CourseOfferingMapper offeringMapper,
    CourseMapper courseMapper,
    CourseOfferingEligibleUserMapper eligibleUserMapper,
    PersonSelectionService personSelectionService,
    CourseVersionMapper courseVersionMapper,
    CourseSnapshotAssembler snapshotAssembler,
    AssessmentExecutionService assessmentExecutionService,
    AssessmentReviewerService assessmentReviewerService) {
    return new CourseOfferingServiceImpl(
      offeringMapper, courseMapper, eligibleUserMapper, personSelectionService,
      courseVersionMapper, snapshotAssembler, assessmentExecutionService, assessmentReviewerService);
  }

  @Bean
  TrainingCourseExecutionPort trainingCourseExecutionPort(
    CourseOfferingMapper offeringMapper,
    CourseVersionMapper courseVersionMapper,
    CourseEnrollmentMapper enrollmentMapper,
    CourseSnapshotAssembler snapshotAssembler,
    AssessmentExecutionService assessmentExecutionService,
    CourseOfferingService courseOfferingService,
    ExamCandidateMapper examCandidateMapper) {
    return new TrainingCourseExecutionAdapter(
      offeringMapper, courseVersionMapper, enrollmentMapper, snapshotAssembler,
      assessmentExecutionService, courseOfferingService, examCandidateMapper);
  }

  @Bean
  ExamService examService(
    ExamMapper examMapper,
    ExamPolicyMapper examPolicyMapper,
    ExamCandidateMapper examCandidateMapper,
    ExamReviewerMapper examReviewerMapper,
    PaperVersionMapper paperVersionMapper,
    PaperMapper paperMapper,
    QuestionBankMapper questionBankMapper,
    PersonSelectionService personSelectionService,
    ExamCandidateProfileSnapshotAssembler profileSnapshotAssembler,
    AssessmentExecutionService assessmentExecutionService) {
    return new ExamServiceImpl(
      examMapper, examPolicyMapper, examCandidateMapper, examReviewerMapper,
      paperVersionMapper, paperMapper, questionBankMapper, personSelectionService,
      mock(ExamPolicyDeleteHelper.class),
      profileSnapshotAssembler,
      mock(ExamReviewerQueryMapper.class),
      mock(ExamCandidateQueryMapper.class),
      assessmentExecutionService,
      new ObjectMapper(), mock(ExamReviewerEligibility.class));
  }

  @Bean
  TrainingAssessmentExecutionPort trainingAssessmentExecutionPort(
    ExamMapper examMapper,
    ExamPolicyMapper examPolicyMapper,
    ExamCandidateMapper examCandidateMapper,
    ExamAttemptMapper examAttemptMapper,
    ExamReviewerMapper examReviewerMapper,
    AssessmentExecutionService assessmentExecutionService,
    ExamService examService,
    ExamAttemptService examAttemptService,
    ExamCandidateProfileSnapshotAssembler profileSnapshotAssembler,
    AssessmentExecutionQuestionPoolService questionPoolService) {
    return new TrainingAssessmentExecutionAdapter(
      examMapper, examPolicyMapper, examCandidateMapper, examAttemptMapper, examReviewerMapper,
      assessmentExecutionService, examService, examAttemptService, profileSnapshotAssembler,
      questionPoolService);
  }

  @Bean
  AssessmentExecutionQuestionPoolService assessmentExecutionQuestionPoolService(
    AssessmentExecutionQuestionPoolMapper mapper, ObjectMapper objectMapper) {
    return new AssessmentExecutionQuestionPoolService(mapper, objectMapper);
  }

  @Bean
  TrainingPlanPublishValidator trainingPlanPublishValidator(
    TrainingPlanStageMapper stageMapper,
    TrainingPlanContentMapper contentMapper,
    TrainingParticipantMapper participantMapper,
    TrainingPlanEvaluationFormMapper evaluationFormBindMapper,
    TrainingStandardRuleMapper standardRuleMapper,
    TrainingResourceAccessHelper resourceAccessHelper,
    EvaluationFormVersionMapper evaluationFormVersionMapper,
    EvaluationFormMapper evaluationFormMapper,
    EvaluationScoreRuleMapper evaluationScoreRuleMapper,
    CourseMapper courseMapper,
    CourseVersionMapper courseVersionMapper,
    ExamMapper examMapper,
    ExamPolicyMapper examPolicyMapper,
    QuestionBankMapper questionBankMapper,
    QuestionMapper questionMapper,
    TrainingPlanQuestionPoolMapper questionPoolMapper,
    AssessmentReviewerService assessmentReviewerService,
    AssessmentExecutionService assessmentExecutionService,
    ExamReviewerMapper examReviewerMapper,
    SysUserMapper sysUserMapper,
    TrainingContentConfigCodec configCodec,
    ObjectMapper objectMapper) {
    return new TrainingPlanPublishValidator(
      stageMapper, contentMapper, participantMapper, evaluationFormBindMapper, standardRuleMapper,
      resourceAccessHelper, evaluationFormVersionMapper, evaluationFormMapper, evaluationScoreRuleMapper, courseMapper,
      courseVersionMapper, examMapper, examPolicyMapper, questionBankMapper,
      new QuestionCandidatePoolService(questionMapper, new QuestionEvaluationModeResolver()),
      new TrainingQuestionPoolSnapshotService(questionPoolMapper, objectMapper),
      assessmentReviewerService, assessmentExecutionService, examReviewerMapper, sysUserMapper,
      configCodec, objectMapper);
  }

  @Bean
  TrainingResultSyncService trainingResultSyncService(
    TrainingPlanMapper planMapper,
    TrainingParticipantMapper participantMapper,
    TrainingPlanStageMapper stageMapper,
    TrainingPlanContentMapper contentMapper,
    TrainingExecutionLinkMapper executionLinkMapper,
    TrainingOfflineSessionMapper offlineSessionMapper,
    TrainingOfflineScoreMapper offlineScoreMapper,
    TrainingParticipantContentResultMapper resultMapper,
    TrainingCourseExecutionPort coursePort,
    TrainingAssessmentExecutionPort assessmentPort,
    TrainingContentConfigCodec configCodec,
    ObjectMapper objectMapper) {
    return new TrainingResultSyncServiceImpl(
      planMapper, participantMapper, stageMapper, contentMapper, executionLinkMapper,
      offlineSessionMapper, offlineScoreMapper, resultMapper,
      coursePort, assessmentPort, configCodec, objectMapper,
      mock(CompositeScoreService.class), mock(CertificateAutoIssueService.class));
  }

  @Bean
  TrainingExecutionProvisioner trainingExecutionProvisioner(
    TrainingPlanMapper planMapper,
    TrainingPlanStageMapper stageMapper,
    TrainingPlanContentMapper contentMapper,
    TrainingParticipantMapper participantMapper,
    TrainingExecutionLinkMapper executionLinkMapper,
    TrainingOfflineSessionMapper offlineSessionMapper,
    TrainingCourseExecutionPort coursePort,
    TrainingAssessmentExecutionPort assessmentPort,
    TrainingContentConfigCodec configCodec,
    TrainingResultSyncService resultSyncService,
    TrainingPlanQuestionPoolMapper questionPoolMapper,
    ObjectMapper objectMapper) {
    return new TrainingExecutionProvisioner(
      planMapper, stageMapper, contentMapper, participantMapper, executionLinkMapper,
      offlineSessionMapper, coursePort, assessmentPort, configCodec, resultSyncService,
      new TrainingQuestionPoolSnapshotService(questionPoolMapper, objectMapper), objectMapper);
  }

  @Bean
  TrainingExecutionService trainingExecutionService(
    TrainingPlanMapper planMapper,
    TrainingPlanStageMapper stageMapper,
    TrainingPlanContentMapper contentMapper,
    TrainingExecutionLinkMapper executionLinkMapper,
    TrainingOfflineSessionMapper offlineSessionMapper,
    TrainingExecutionProvisioner provisioner,
    TrainingCourseExecutionPort coursePort) {
    return new TrainingExecutionServiceImpl(
      planMapper, stageMapper, contentMapper, executionLinkMapper, offlineSessionMapper, provisioner,
      coursePort);
  }

  @Bean
  TrainingPlanLifecycleService trainingPlanLifecycleService(
    TrainingPlanMapper planMapper,
    TrainingPlanStageMapper stageMapper,
    TrainingPlanContentMapper contentMapper,
    TrainingParticipantMapper participantMapper,
    TrainingExecutionLinkMapper executionLinkMapper,
    TrainingOfflineSessionMapper offlineSessionMapper,
    TrainingParticipantContentResultMapper resultMapper,
    TrainingCourseExecutionPort coursePort,
    TrainingAssessmentExecutionPort assessmentPort,
    TrainingResultSyncService resultSyncService,
    PlanHoursWarningRefreshService planHoursWarningRefreshService) {
    return new TrainingPlanLifecycleServiceImpl(
      planMapper, stageMapper, contentMapper, participantMapper, executionLinkMapper,
      offlineSessionMapper, resultMapper, coursePort, assessmentPort, resultSyncService,
      planHoursWarningRefreshService);
  }

  @Bean
  TrainingPlanService trainingPlanService(
    TrainingPlanMapper trainingPlanMapper,
    TrainingPlanStageMapper trainingPlanStageMapper,
    TrainingPlanContentMapper trainingPlanContentMapper,
    TrainingTemplateStageMapper trainingTemplateStageMapper,
    TrainingTemplateContentMapper trainingTemplateContentMapper,
    TrainingPlanEvaluationFormMapper trainingPlanEvaluationFormMapper,
    TrainingParticipantMapper trainingParticipantMapper,
    TrainingStandardRuleMapper trainingStandardRuleMapper,
    EvaluationFormVersionMapper evaluationFormVersionMapper,
    EvaluationFormMapper evaluationFormMapper,
    PersonSelectionService personSelectionService,
    TrainingResourceAccessHelper resourceAccessHelper,
    ObjectMapper objectMapper,
    TrainingPlanPublishValidator publishValidator,
    TrainingContentConfigCodec configCodec,
    TrainingExecutionService trainingExecutionService,
    TrainingPlanLifecycleService lifecycleService,
    PlanHoursWarningRefreshService planHoursWarningRefreshService, TrainingPlanApprovalService approvalService) {
    return new TrainingPlanServiceImpl(
      trainingPlanMapper, trainingPlanStageMapper, trainingPlanContentMapper,
      trainingTemplateStageMapper, trainingTemplateContentMapper,
      trainingPlanEvaluationFormMapper, trainingParticipantMapper, trainingStandardRuleMapper,
      evaluationFormVersionMapper, evaluationFormMapper, personSelectionService, resourceAccessHelper, objectMapper,
      publishValidator, configCodec, trainingExecutionService, lifecycleService, planHoursWarningRefreshService, approvalService);
  }

  @Bean
  TrainingPlanApprovalService trainingPlanApprovalService(TrainingPlanMapper plans, TrainingPlanApprovalMapper approvals,
    SysUserMapper users, com.sts.admin.mapper.SysMenuMapper menus) {
    return new TrainingPlanApprovalService(plans, approvals, users, menus);
  }

  @Bean
  PlanHoursWarningRefreshService planHoursWarningRefreshService() {
    return org.mockito.Mockito.mock(PlanHoursWarningRefreshService.class);
  }
}
