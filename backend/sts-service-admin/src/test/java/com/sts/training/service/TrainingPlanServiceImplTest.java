package com.sts.training.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.common.exception.BusinessException;
import com.sts.course.entity.Course;
import com.sts.course.entity.CourseVersion;
import com.sts.course.mapper.CourseMapper;
import com.sts.course.mapper.CourseVersionMapper;
import com.sts.evaluation.entity.EvaluationForm;
import com.sts.evaluation.entity.EvaluationFormVersion;
import com.sts.evaluation.mapper.EvaluationFormMapper;
import com.sts.evaluation.mapper.EvaluationFormVersionMapper;
import com.sts.exam.mapper.ExamMapper;
import com.sts.exam.mapper.ExamPolicyMapper;
import com.sts.person.dto.PersonSelectionRequest;
import com.sts.person.service.PersonSelectionService;
import com.sts.question.mapper.QuestionBankMapper;
import com.sts.training.dto.*;
import com.sts.training.entity.*;
import com.sts.training.mapper.*;
import com.sts.training.vo.TrainingGenerateExecutionsResultVO;
import com.sts.training.vo.TrainingPlanVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TrainingPlanServiceImplTest {

  private TrainingPlanMapper planMapper;
  private TrainingPlanStageMapper stageMapper;
  private TrainingPlanContentMapper contentMapper;
  private TrainingTemplateStageMapper templateStageMapper;
  private TrainingTemplateContentMapper templateContentMapper;
  private TrainingPlanEvaluationFormMapper evalFormMapper;
  private TrainingParticipantMapper participantMapper;
  private TrainingStandardRuleMapper standardRuleMapper;
  private EvaluationFormVersionMapper evaluationFormVersionMapper;
  private EvaluationFormMapper evaluationFormMapper;
  private PersonSelectionService personSelectionService;
  private TrainingResourceAccessHelper resourceAccessHelper;
  private ObjectMapper objectMapper;
  private CourseMapper courseMapper;
  private CourseVersionMapper courseVersionMapper;
  private TrainingExecutionService trainingExecutionService;
  private TrainingPlanServiceImpl service;

  @BeforeEach
  void setUp() {
    planMapper = mock(TrainingPlanMapper.class);
    stageMapper = mock(TrainingPlanStageMapper.class);
    contentMapper = mock(TrainingPlanContentMapper.class);
    templateStageMapper = mock(TrainingTemplateStageMapper.class);
    templateContentMapper = mock(TrainingTemplateContentMapper.class);
    evalFormMapper = mock(TrainingPlanEvaluationFormMapper.class);
    participantMapper = mock(TrainingParticipantMapper.class);
    standardRuleMapper = mock(TrainingStandardRuleMapper.class);
    evaluationFormVersionMapper = mock(EvaluationFormVersionMapper.class);
    evaluationFormMapper = mock(EvaluationFormMapper.class);
    personSelectionService = mock(PersonSelectionService.class);
    resourceAccessHelper = mock(TrainingResourceAccessHelper.class);
    objectMapper = new ObjectMapper().findAndRegisterModules();
    courseMapper = mock(CourseMapper.class);
    courseVersionMapper = mock(CourseVersionMapper.class);
    trainingExecutionService = mock(TrainingExecutionService.class);
    TrainingContentConfigCodec codec = new TrainingContentConfigCodec(objectMapper);
    TrainingPlanPublishValidator validator = new TrainingPlanPublishValidator(
      stageMapper, contentMapper, participantMapper, evalFormMapper, standardRuleMapper,
      resourceAccessHelper, evaluationFormVersionMapper, evaluationFormMapper,
      mock(com.sts.evaluation.mapper.EvaluationScoreRuleMapper.class),
      courseMapper, courseVersionMapper, mock(ExamMapper.class), mock(ExamPolicyMapper.class),
      mock(QuestionBankMapper.class), mock(com.sts.question.service.QuestionCandidatePoolService.class),
      mock(TrainingQuestionPoolSnapshotService.class),
      mock(com.sts.exam.service.AssessmentReviewerService.class),
      mock(com.sts.exam.service.AssessmentExecutionService.class),
      mock(com.sts.exam.mapper.ExamReviewerMapper.class),
      mock(com.sts.admin.mapper.SysUserMapper.class),
      codec, objectMapper);
    var approval = mock(TrainingPlanApprovalService.class);
    when(approval.lockForEdit(any())).thenAnswer(inv -> planMapper.selectById(inv.getArgument(0)));
    when(approval.lock(any())).thenAnswer(inv -> planMapper.selectById(inv.getArgument(0)));
    when(planMapper.updateById(any(TrainingPlan.class))).thenReturn(1);
    service = new TrainingPlanServiceImpl(planMapper, stageMapper, contentMapper,
                                          templateStageMapper, templateContentMapper, evalFormMapper,
                                          participantMapper, standardRuleMapper,
                                          evaluationFormVersionMapper, evaluationFormMapper,
                                          personSelectionService,
                                          resourceAccessHelper,
                                          objectMapper,
                                          validator,
                                          codec,
                                          trainingExecutionService,
                                          mock(TrainingPlanLifecycleService.class),
                                          mock(com.sts.regulation.service.PlanHoursWarningRefreshService.class), approval);
  }

  @Test void approvedBasicChangeProtectsExecutionAndFrozenHistory() {
    var plan=draftPlan();plan.setPublishStatus("RUNNING");plan.setPublishedTime(LocalDateTime.of(2026,10,1,9,0));plan.setSourceSnapshotHash("原快照");
    var r=new TrainingPlanChangeRequest();r.setChangeType("BASIC");var basic=new TrainingPlanBasicChangeRequest();basic.setPlanName("新名称");basic.setLocation("新地点");r.setBasic(basic);
    service.applyApprovedChange(plan,r);
    assertThat(plan.getPublishStatus()).isEqualTo("RUNNING");assertThat(plan.getSourceSnapshotHash()).isEqualTo("原快照");assertThat(plan.getPublishedTime()).isEqualTo(LocalDateTime.of(2026,10,1,9,0));
    assertThat(plan.getManagerUserId()).isNull();verify(planMapper).updateById(plan);verifyNoInteractions(stageMapper,contentMapper,participantMapper,trainingExecutionService);
  }

  @Test
  @DisplayName("按组绑定只追加当前具体人员，不删除既有快照或重复插入")
  void groupBindingPreservesPreviousSnapshot() {
    var plan = new TrainingPlan(); plan.setTrainingPlanId(100L); plan.setOrganizationId(1L); plan.setPublishStatus("DRAFT");
    when(planMapper.selectById(100L)).thenReturn(plan);
    var selection = new PersonSelectionRequest(); selection.setSelectionType("GROUP"); selection.setSubjectIds(List.of(1L));
    var request = new BindTrainingSelectionRequest(); request.setSelection(selection);
    when(personSelectionService.resolve(selection)).thenReturn(List.of(7L,8L));
    var previous = new TrainingParticipant(); previous.setUserId(7L); previous.setTrainingPlanId(100L);
    when(participantMapper.selectOne(any())).thenReturn(previous, null);
    var result = service.bindSelection(100L,request);
    assertThat(result).extracting(com.sts.training.vo.TrainingParticipantVO::getUserId).containsExactly(7L,8L);
    verify(participantMapper,times(1)).insert(argThat((TrainingParticipant p) -> p.getUserId().equals(8L)));
    verify(participantMapper,never()).updateById(any(TrainingParticipant.class));
  }

  @Test
  @DisplayName("bindSelection：部门选人最终写具体参训人")
  void bindSelection_createsConcreteParticipants() {
    TrainingPlan plan = new TrainingPlan();
    plan.setTrainingPlanId(100L);
    plan.setOrganizationId(1L);
    plan.setPublishStatus("DRAFT");
    when(planMapper.selectById(100L)).thenReturn(plan);
    when(personSelectionService.resolve(any())).thenReturn(List.of(10L));
    when(participantMapper.selectOne(any())).thenReturn(null);

    BindTrainingSelectionRequest request = new BindTrainingSelectionRequest();
    PersonSelectionRequest selection = new PersonSelectionRequest();
    selection.setSelectionType("DEPT");
    selection.setSubjectIds(List.of(20L));
    request.setSelection(selection);

    service.bindSelection(100L, request);

    verify(participantMapper).insert(argThat((TrainingParticipant participant) ->
                                               participant.getUserId().equals(10L)
                                                 && participant.getParticipantOrgId().equals(1L)
                                                 && "ASSIGN".equals(participant.getJoinSource())));
  }

  @Test
  @DisplayName("create：创建草稿计划")
  void create_ok() {
    when(resourceAccessHelper.requireTypeForUse(any())).thenReturn(new TrainingType());
    when(planMapper.insert(any(TrainingPlan.class))).thenAnswer(inv -> {
      TrainingPlan p = inv.getArgument(0);
      p.setTrainingPlanId(100L);
      return 1;
    });
    TrainingPlanCreateRequest req = new TrainingPlanCreateRequest();
    req.setPlanCode("P1");
    req.setPlanName("计划1");
    req.setTrainingTypeId(1L);
    req.setTrainingNature("INITIAL");
    req.setStartTime(LocalDateTime.now());
    req.setEndTime(LocalDateTime.now().plusDays(1));
    req.setStatus(1);
    assertThat(service.create(req)).isEqualTo(100L);
    verify(planMapper).insert(argThat((TrainingPlan plan) -> "INITIAL".equals(plan.getTrainingNature())));
  }

  @Test
  @DisplayName("create：合格已发布模板在同一事务复制阶段内容")
  void create_publishedTemplate_copiesSyllabus() {
    when(resourceAccessHelper.requireTypeForUse(1L)).thenReturn(new TrainingType());
    when(resourceAccessHelper.requireTemplateForUse(20L)).thenReturn(publishedTemplate());
    when(planMapper.insert(any(TrainingPlan.class))).thenAnswer(inv -> {
      TrainingPlan p = inv.getArgument(0);
      p.setTrainingPlanId(100L);
      return 1;
    });
    TrainingTemplateStage templateStage = new TrainingTemplateStage();
    templateStage.setTemplateStageId(50L);
    templateStage.setStageName("理论");
    templateStage.setStageType("ONLINE");
    templateStage.setSortOrder(1);
    when(templateStageMapper.selectList(any())).thenReturn(List.of(templateStage));
    when(stageMapper.insert(any(TrainingPlanStage.class))).thenAnswer(inv -> {
      TrainingPlanStage stage = inv.getArgument(0);
      stage.setPlanStageId(200L);
      return 1;
    });
    TrainingTemplateContent templateContent = new TrainingTemplateContent();
    templateContent.setContentType("COURSE");
    templateContent.setResourceId(99L);
    templateContent.setContentName("安全课");
    templateContent.setIsRequired(1);
    templateContent.setSortOrder(1);
    when(templateContentMapper.selectList(any())).thenReturn(List.of(templateContent));
    TrainingPlanCreateRequest req = createRequest();
    req.setSourceTemplateId(20L);

    assertThat(service.create(req)).isEqualTo(100L);
    verify(planMapper).insert(argThat((TrainingPlan plan) ->
                                        Objects.equals(20L, plan.getSourceTemplateId())
                                          && plan.getTrainingStandardId() == null));
    verify(stageMapper).insert(argThat((TrainingPlanStage stage) ->
                                         "理论".equals(stage.getStageName())
                                           && Objects.equals(50L, stage.getSourceTemplateStageId())
                                           && "PENDING".equals(stage.getStageStatus())));
    verify(contentMapper).insert(argThat((TrainingPlanContent content) ->
                                           "COURSE".equals(content.getContentType())
                                             && Objects.equals(99L, content.getResourceId())
                                             && content.getExecutionConfigJson() == null));
  }

  @Test
  @DisplayName("create：模板类型不一致返回 400/8157 且不插入计划")
  void create_templateTypeMismatch_throwsWithoutInsert() {
    when(resourceAccessHelper.requireTypeForUse(1L)).thenReturn(new TrainingType());
    TrainingTemplate template = publishedTemplate();
    template.setTrainingTypeId(2L);
    when(resourceAccessHelper.requireTemplateForUse(20L)).thenReturn(template);
    TrainingPlanCreateRequest req = createRequest();
    req.setSourceTemplateId(20L);

    assertThatThrownBy(() -> service.create(req))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 8157)
      .hasFieldOrPropertyWithValue("status", org.springframework.http.HttpStatus.BAD_REQUEST);
    verify(planMapper, never()).insert(any(TrainingPlan.class));
  }

  @Test
  @DisplayName("applyTemplate：草稿覆盖复制课表")
  void applyTemplate_draft_replacesSyllabus() {
    TrainingPlan plan = draftPlan();
    when(planMapper.selectById(100L)).thenReturn(plan);
    when(resourceAccessHelper.requireTemplateForUse(20L)).thenReturn(publishedTemplate());
    when(planMapper.updateById(any(TrainingPlan.class))).thenReturn(1);
    when(stageMapper.selectList(any())).thenReturn(List.of());
    TrainingTemplateStage templateStage = new TrainingTemplateStage();
    templateStage.setTemplateStageId(50L);
    templateStage.setStageName("实操");
    templateStage.setStageType("OFFLINE");
    templateStage.setSortOrder(1);
    when(templateStageMapper.selectList(any())).thenReturn(List.of(templateStage));
    when(stageMapper.insert(any(TrainingPlanStage.class))).thenAnswer(inv -> {
      TrainingPlanStage stage = inv.getArgument(0);
      stage.setPlanStageId(201L);
      return 1;
    });
    when(templateContentMapper.selectList(any())).thenReturn(List.of());
    TrainingPlanApplyTemplateRequest req = new TrainingPlanApplyTemplateRequest();
    req.setVersion(0);
    req.setSourceTemplateId(20L);

    service.applyTemplate(100L, req);

    assertThat(plan.getSourceTemplateId()).isEqualTo(20L);
    verify(stageMapper).update(isNull(), any());
    verify(stageMapper).insert(argThat((TrainingPlanStage stage) -> "实操".equals(stage.getStageName())));
  }

  @Test
  @DisplayName("applyTemplate：已发布计划返回 409/8158")
  void applyTemplate_published_throws8158() {
    TrainingPlan plan = draftPlan();
    plan.setPublishStatus("PUBLISHED");
    when(planMapper.selectById(100L)).thenReturn(plan);
    TrainingPlanApplyTemplateRequest req = new TrainingPlanApplyTemplateRequest();
    req.setVersion(0);
    req.setSourceTemplateId(20L);

    assertThatThrownBy(() -> service.applyTemplate(100L, req))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 8158)
      .hasFieldOrPropertyWithValue("status", org.springframework.http.HttpStatus.CONFLICT);
    verify(planMapper, never()).updateById(any(TrainingPlan.class));
    verify(stageMapper, never()).insert(any(TrainingPlanStage.class));
  }

  @Test
  @DisplayName("update：只改来源模板 ID 不复制课表")
  void update_sourceTemplateId_doesNotCopySyllabus() {
    TrainingPlan plan = draftPlan();
    when(planMapper.selectById(100L)).thenReturn(plan);
    when(resourceAccessHelper.requireTypeForUse(1L)).thenReturn(new TrainingType());
    when(resourceAccessHelper.requireTemplateForUse(20L)).thenReturn(publishedTemplate());
    when(planMapper.updateById(any(TrainingPlan.class))).thenReturn(1);
    TrainingPlanUpdateRequest req = new TrainingPlanUpdateRequest();
    req.setVersion(0);
    req.setPlanName("计划1");
    req.setTrainingTypeId(1L);
    req.setTrainingNature("INITIAL");
    req.setSourceTemplateId(20L);
    req.setStartTime(LocalDateTime.now());
    req.setEndTime(LocalDateTime.now().plusDays(1));
    req.setStatus(1);

    service.update(100L, req);

    verify(planMapper).updateById(argThat((TrainingPlan updated) -> Objects.equals(20L,
                                                                                   updated.getSourceTemplateId())));
    verify(templateStageMapper, never()).selectList(any());
    verify(stageMapper, never()).insert(any(TrainingPlanStage.class));
  }

  @Test
  @DisplayName("update：允许用 null 清除标准和模板关联")
  void update_nullReferences_clearAssociations() {
    TrainingPlan plan = draftPlan();
    plan.setTrainingStandardId(30L);
    plan.setSourceTemplateId(20L);
    when(planMapper.selectById(100L)).thenReturn(plan);
    when(resourceAccessHelper.requireTypeForUse(1L)).thenReturn(new TrainingType());
    when(resourceAccessHelper.requireStandardForUse(null)).thenReturn(null);
    when(resourceAccessHelper.requireTemplateForUse(null)).thenReturn(null);
    when(planMapper.updateById(any(TrainingPlan.class))).thenReturn(1);
    TrainingPlanUpdateRequest req = new TrainingPlanUpdateRequest();
    req.setVersion(0);
    req.setPlanName("计划1");
    req.setTrainingTypeId(1L);
    req.setTrainingNature("INITIAL");
    req.setStartTime(LocalDateTime.now());
    req.setEndTime(LocalDateTime.now().plusDays(1));
    req.setStatus(1);

    service.update(100L, req);

    verify(planMapper).updateById(argThat((TrainingPlan updated) -> updated.getTrainingStandardId() == null
      && updated.getSourceTemplateId() == null));
  }

  @Test
  @DisplayName("publish：缺少阶段、必修内容或参训人时拒绝发布")
  void publish_missingStructure_throws() {
    TrainingPlan plan = draftPlan();
    when(planMapper.selectById(100L)).thenReturn(plan);
    when(resourceAccessHelper.requireTypeForUse(1L)).thenReturn(type());
    when(resourceAccessHelper.requireStandardForUse(null)).thenReturn(null);
    when(resourceAccessHelper.requireTemplateForUse(null)).thenReturn(null);
    when(stageMapper.selectList(any())).thenReturn(List.of());
    when(participantMapper.selectCount(any())).thenReturn(0L);

    assertThatThrownBy(() -> service.publish(100L, 0))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 8113);
    assertThat(plan.getPublishStatus()).isEqualTo("DRAFT");
  }

  @Test
  @DisplayName("assignParticipant：已发布计划不可修改参训名单")
  void assignParticipant_published_throws() {
    TrainingPlan plan = draftPlan();
    plan.setPublishStatus("PUBLISHED");
    when(planMapper.selectById(100L)).thenReturn(plan);
    TrainingParticipantAssignRequest req = new TrainingParticipantAssignRequest();
    req.setUserId(10L);
    assertThatThrownBy(() -> service.assignParticipant(100L, req))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 8109);
    verify(participantMapper, never()).insert(any(TrainingParticipant.class));
  }

  @Test
  @DisplayName("bindSelection：已发布计划不可修改参训名单")
  void bindSelection_published_throws() {
    TrainingPlan plan = draftPlan();
    plan.setPublishStatus("PUBLISHED");
    when(planMapper.selectById(100L)).thenReturn(plan);
    BindTrainingSelectionRequest request = new BindTrainingSelectionRequest();
    PersonSelectionRequest selection = new PersonSelectionRequest();
    selection.setSelectionType("USER");
    selection.setSubjectIds(List.of(10L));
    request.setSelection(selection);
    assertThatThrownBy(() -> service.bindSelection(100L, request))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 8109);
  }

  @Test
  @DisplayName("publish：冻结课程版本且来源课程新版本不影响已发布计划")
  void publish_freezesCourseVersion() {
    TrainingPlan plan = draftPlan();
    when(planMapper.selectById(100L)).thenReturn(plan);
    when(resourceAccessHelper.requireTypeForUse(1L)).thenReturn(type());
    when(resourceAccessHelper.requireStandardForUse(null)).thenReturn(null);
    when(resourceAccessHelper.requireTemplateForUse(null)).thenReturn(null);
    when(evalFormMapper.selectList(any())).thenReturn(List.of());
    when(standardRuleMapper.selectList(any())).thenReturn(List.of());
    when(planMapper.updateById(any(TrainingPlan.class))).thenReturn(1);
    when(participantMapper.selectCount(any())).thenReturn(1L);

    TrainingPlanStage stage = new TrainingPlanStage();
    stage.setPlanStageId(200L);
    when(stageMapper.selectList(any())).thenReturn(List.of(stage));
    TrainingPlanContent content = courseContent();
    when(contentMapper.selectList(any())).thenReturn(List.of(content));
    when(contentMapper.updateById(any(TrainingPlanContent.class))).thenReturn(1);

    Course course = new Course();
    course.setCourseId(999L);
    course.setOrganizationId(1L);
    course.setCourseCode("C-1");
    course.setCourseName("安全课");
    course.setPublishStatus("PUBLISHED");
    course.setStatus(1);
    course.setCurrentVersionId(10L);
    when(courseMapper.selectById(999L)).thenReturn(course);
    CourseVersion version = new CourseVersion();
    version.setCourseVersionId(10L);
    version.setVersionNo(2);
    when(courseVersionMapper.selectById(10L)).thenReturn(version);
    service.publish(100L, 0);

    assertThat(plan.getPublishStatus()).isEqualTo("PUBLISHED");
    assertThat(plan.getApplicableRequiredHours()).isEqualByComparingTo("8");
    assertThat(plan.getTrainingTypeSnapshotJson())
      .contains("\"schemaVersion\":2")
      .contains("\"trainingNature\":\"INITIAL\"")
      .contains("\"applicableRequiredHours\":8");
    assertThat(content.getResourceVersionId()).isEqualTo(10L);
    assertThat(content.getResourceSnapshotHash()).isNotBlank();
    course.setCurrentVersionId(99L);
    assertThat(content.getResourceVersionId()).isEqualTo(10L);
  }

  private void stubDraftReplaceStages() {
    TrainingPlan plan = new TrainingPlan();
    plan.setTrainingPlanId(100L);
    plan.setPublishStatus("DRAFT");
    plan.setVersion(1);
    when(planMapper.selectById(100L)).thenReturn(plan);
    when(planMapper.updateById(any(TrainingPlan.class))).thenReturn(1);
    when(stageMapper.selectList(any())).thenReturn(List.of());
    when(stageMapper.insert(any(TrainingPlanStage.class))).thenAnswer(inv -> {
      TrainingPlanStage s = inv.getArgument(0);
      s.setPlanStageId(200L);
      return 1;
    });
  }

  private TrainingPlanStagesReplaceRequest stagesRequest(TrainingPlanContentRequest content) {
    TrainingPlanStagesReplaceRequest req = new TrainingPlanStagesReplaceRequest();
    req.setVersion(1);
    TrainingPlanStageRequest stage = new TrainingPlanStageRequest();
    stage.setStageName("阶段1");
    stage.setStageType("ONLINE");
    stage.setSortOrder(1);
    stage.setContents(List.of(content));
    req.setStages(List.of(stage));
    return req;
  }

  private TrainingPlanContentRequest examContent(BigDecimal requiredHours) {
    TrainingPlanContentRequest content = new TrainingPlanContentRequest();
    content.setContentType("EXAM");
    content.setContentName("理论考");
    content.setIsRequired(1);
    content.setRequiredHours(requiredHours);
    content.setSortOrder(1);
    return content;
  }

  private TrainingPlan draftPlan() {
    TrainingPlan plan = new TrainingPlan();
    plan.setTrainingPlanId(100L);
    plan.setOrganizationId(1L);
    plan.setTrainingTypeId(1L);
    plan.setTrainingNature("INITIAL");
    plan.setVersion(0);
    plan.setPublishStatus("DRAFT");
    return plan;
  }

  private TrainingTemplate publishedTemplate() {
    TrainingTemplate template = new TrainingTemplate();
    template.setTrainingTemplateId(20L);
    template.setOrganizationId(1L);
    template.setTrainingTypeId(1L);
    template.setTrainingStandardId(9L);
    template.setPublishStatus("PUBLISHED");
    template.setStatus(1);
    return template;
  }

  private TrainingPlanCreateRequest createRequest() {
    TrainingPlanCreateRequest req = new TrainingPlanCreateRequest();
    req.setPlanCode("P1");
    req.setPlanName("计划1");
    req.setTrainingTypeId(1L);
    req.setTrainingNature("INITIAL");
    req.setStartTime(LocalDateTime.now());
    req.setEndTime(LocalDateTime.now().plusDays(1));
    req.setStatus(1);
    return req;
  }

  private TrainingType type() {
    TrainingType type = new TrainingType();
    type.setTrainingTypeId(1L);
    type.setTypeCode("T");
    type.setTypeName("类型");
    type.setInitialHours(new java.math.BigDecimal("8"));
    type.setReviewHours(new java.math.BigDecimal("4"));
    return type;
  }

  private TrainingPlanContent courseContent() {
    TrainingPlanContent content = new TrainingPlanContent();
    content.setPlanContentId(300L);
    content.setPlanStageId(200L);
    content.setContentType("COURSE");
    content.setResourceId(999L);
    content.setContentName("课程");
    content.setIsRequired(1);
    content.setRequiredHours(new java.math.BigDecimal("8"));
    content.setExecutionConfigJson("{\"completionRule\":\"ALL_REQUIRED_ITEMS\"}");
    return content;
  }

  @Test
  @DisplayName("generateExecutions：委托执行协调器")
  void generateExecutions_delegatesToExecutionService() {
    TrainingGenerateExecutionsResultVO delegated = new TrainingGenerateExecutionsResultVO();
    delegated.setTrainingPlanId(100L);
    delegated.setExecutionLinksCreated(1);
    when(trainingExecutionService.generateExecutions(100L)).thenReturn(delegated);
    var result = service.generateExecutions(100L);
    assertThat(result.getExecutionLinksCreated()).isEqualTo(1);
    verify(trainingExecutionService).generateExecutions(100L);
  }

  @Test
  @DisplayName("generateExecutions：草稿计划由协调器拒绝")
  void generateExecutions_draft_throws() {
    when(trainingExecutionService.generateExecutions(100L))
      .thenThrow(new BusinessException(org.springframework.http.HttpStatus.BAD_REQUEST,
                                       8108,
                                       "仅已发布计划可生成执行实例"));
    assertThatThrownBy(() -> service.generateExecutions(100L)).isInstanceOf(BusinessException.class);
  }

  @Test
  @DisplayName("update：版本冲突返回 409/8104")
  void update_versionConflict_throws() {
    TrainingPlan plan = new TrainingPlan();
    plan.setTrainingPlanId(100L);
    plan.setOrganizationId(1L);
    plan.setPublishStatus("DRAFT");
    plan.setVersion(2);
    when(planMapper.selectById(100L)).thenReturn(plan);
    when(resourceAccessHelper.requireTypeForUse(any())).thenReturn(new TrainingType());
    when(planMapper.updateById(any(TrainingPlan.class))).thenReturn(0);
    TrainingPlanUpdateRequest req = new TrainingPlanUpdateRequest();
    req.setVersion(1);
    req.setPlanName("计划1");
    req.setTrainingTypeId(1L);
    req.setTrainingNature("REVIEW");
    req.setStartTime(LocalDateTime.now());
    req.setEndTime(LocalDateTime.now().plusDays(1));
    req.setStatus(1);

    assertThatThrownBy(() -> service.update(100L, req))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 8104);
  }

  @Test
  @DisplayName("replaceStages：版本冲突时不删除既有阶段与内容")
  void replaceStages_versionConflict_doesNotDeleteStages() {
    TrainingPlan plan = new TrainingPlan();
    plan.setTrainingPlanId(100L);
    plan.setPublishStatus("DRAFT");
    plan.setVersion(2);
    when(planMapper.selectById(100L)).thenReturn(plan);
    when(planMapper.updateById(any(TrainingPlan.class))).thenReturn(0);
    TrainingPlanStagesReplaceRequest req = new TrainingPlanStagesReplaceRequest();
    req.setVersion(1);

    assertThatThrownBy(() -> service.replaceStages(100L, req))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 8104);

    verify(stageMapper, never()).update(any(), any());
    verify(contentMapper, never()).update(any(), any());
  }

  @Test
  @DisplayName("replaceStages：版本一致时先加锁再替换")
  void replaceStages_versionMatched_replacesAfterLock() {
    TrainingPlan plan = new TrainingPlan();
    plan.setTrainingPlanId(100L);
    plan.setPublishStatus("DRAFT");
    plan.setVersion(1);
    when(planMapper.selectById(100L)).thenReturn(plan);
    when(planMapper.updateById(any(TrainingPlan.class))).thenReturn(1);
    when(stageMapper.selectList(any())).thenReturn(List.of());
    when(stageMapper.insert(any(TrainingPlanStage.class))).thenAnswer(inv -> {
      TrainingPlanStage s = inv.getArgument(0);
      s.setPlanStageId(200L);
      return 1;
    });
    TrainingPlanStagesReplaceRequest req = new TrainingPlanStagesReplaceRequest();
    req.setVersion(1);
    TrainingPlanStageRequest stage = new TrainingPlanStageRequest();
    stage.setStageName("阶段1");
    stage.setStageType("ONLINE");
    stage.setSortOrder(1);
    req.setStages(List.of(stage));

    service.replaceStages(100L, req);

    verify(planMapper).updateById(plan);
    verify(stageMapper).update(isNull(), any());
    verify(stageMapper).insert(any(TrainingPlanStage.class));
  }

  @Test
  @DisplayName("replaceStages：考试不填学时落库 0")
  void replaceStages_examWithoutHours_persistsZero() {
    stubDraftReplaceStages();
    TrainingPlanStagesReplaceRequest req = stagesRequest(examContent(null));

    service.replaceStages(100L, req);

    verify(contentMapper).insert(argThat((TrainingPlanContent content) ->
                                           "EXAM".equals(content.getContentType())
                                             && content.getRequiredHours().compareTo(BigDecimal.ZERO) == 0));
  }

  @Test
  @DisplayName("replaceStages：考试填正数学时返回 400/8165 且不写库")
  void replaceStages_examPositiveHours_throws8165() {
    stubDraftReplaceStages();
    TrainingPlanStagesReplaceRequest req = stagesRequest(examContent(new BigDecimal("2")));

    assertThatThrownBy(() -> service.replaceStages(100L, req))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 8165)
      .hasFieldOrPropertyWithValue("status", org.springframework.http.HttpStatus.BAD_REQUEST)
      .hasMessageContaining("考试");
    verify(planMapper, never()).updateById(any(TrainingPlan.class));
    verify(contentMapper, never()).insert(any(TrainingPlanContent.class));
    verify(stageMapper, never()).update(any(), any());
  }

  @Test
  @DisplayName("replaceStages：题库填正数学时返回 400/8165 且不写库")
  void replaceStages_questionBankPositiveHours_throws8165() {
    stubDraftReplaceStages();
    TrainingPlanContentRequest content = examContent(new BigDecimal("1"));
    content.setContentType("QUESTION_BANK");
    TrainingPlanStagesReplaceRequest req = stagesRequest(content);

    assertThatThrownBy(() -> service.replaceStages(100L, req))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 8165)
      .hasMessageContaining("题库");
    verify(contentMapper, never()).insert(any(TrainingPlanContent.class));
  }

  @Test
  @DisplayName("replaceStages：课程正数学时仍可保存")
  void replaceStages_courseHours_stillSaved() {
    stubDraftReplaceStages();
    TrainingPlanContentRequest content = examContent(new BigDecimal("8"));
    content.setContentType("COURSE");
    TrainingPlanStagesReplaceRequest req = stagesRequest(content);

    service.replaceStages(100L, req);

    verify(contentMapper).insert(argThat((TrainingPlanContent saved) ->
                                           "COURSE".equals(saved.getContentType())
                                             && saved.getRequiredHours().compareTo(new BigDecimal("8")) == 0));
  }

  @Test
  @DisplayName("applyTemplate：模板考试学时拷贝为 0")
  void applyTemplate_examHoursFromTemplate_zeroed() {
    TrainingPlan plan = draftPlan();
    when(planMapper.selectById(100L)).thenReturn(plan);
    when(resourceAccessHelper.requireTemplateForUse(20L)).thenReturn(publishedTemplate());
    when(planMapper.updateById(any(TrainingPlan.class))).thenReturn(1);
    when(stageMapper.selectList(any())).thenReturn(List.of());
    TrainingTemplateStage templateStage = new TrainingTemplateStage();
    templateStage.setTemplateStageId(50L);
    templateStage.setStageName("考核");
    templateStage.setStageType("ONLINE");
    templateStage.setSortOrder(1);
    when(templateStageMapper.selectList(any())).thenReturn(List.of(templateStage));
    when(stageMapper.insert(any(TrainingPlanStage.class))).thenAnswer(inv -> {
      TrainingPlanStage stage = inv.getArgument(0);
      stage.setPlanStageId(201L);
      return 1;
    });
    TrainingTemplateContent templateContent = new TrainingTemplateContent();
    templateContent.setContentType("EXAM");
    templateContent.setResourceId(88L);
    templateContent.setContentName("理论考");
    templateContent.setIsRequired(1);
    templateContent.setRequiredHours(new BigDecimal("2"));
    templateContent.setSortOrder(1);
    when(templateContentMapper.selectList(any())).thenReturn(List.of(templateContent));
    TrainingPlanApplyTemplateRequest req = new TrainingPlanApplyTemplateRequest();
    req.setVersion(0);
    req.setSourceTemplateId(20L);

    service.applyTemplate(100L, req);

    verify(contentMapper).insert(argThat((TrainingPlanContent content) ->
                                           "EXAM".equals(content.getContentType())
                                             && content.getRequiredHours().compareTo(BigDecimal.ZERO) == 0));
  }

  @Test
  @DisplayName("bindEvaluationForms：版本冲突时不删除既有绑定")
  void bindEvaluationForms_versionConflict_doesNotDeleteBindings() {
    TrainingPlan plan = new TrainingPlan();
    plan.setTrainingPlanId(100L);
    plan.setPublishStatus("DRAFT");
    plan.setVersion(2);
    when(planMapper.selectById(100L)).thenReturn(plan);
    when(planMapper.updateById(any(TrainingPlan.class))).thenReturn(0);
    TrainingPlanEvaluationFormBindRequest req = new TrainingPlanEvaluationFormBindRequest();
    req.setVersion(1);
    req.setForms(List.of());

    assertThatThrownBy(() -> service.bindEvaluationForms(100L, req))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 8104);

    verify(evalFormMapper, never()).delete(any());
    verifyNoInteractions(evaluationFormVersionMapper);
  }

  @Test
  @DisplayName("get：没有评价表单绑定时返回空集合")
  void get_withoutEvaluationForms_returnsEmptyList() {
    when(planMapper.selectById(100L)).thenReturn(draftPlan());
    when(stageMapper.selectList(any())).thenReturn(List.of());
    when(evalFormMapper.selectList(any())).thenReturn(List.of());

    TrainingPlanVO result = service.get(100L);

    assertThat(result.getEvaluationForms()).isEmpty();
  }

  @Test
  @DisplayName("get：读回评价表单绑定摘要并按用途和绑定 ID 排序")
  void get_withEvaluationForms_returnsSortedVersionSummaries() {
    when(planMapper.selectById(100L)).thenReturn(draftPlan());
    when(stageMapper.selectList(any())).thenReturn(List.of());
    TrainingPlanEvaluationForm participant = binding(12L, 502L, "PARTICIPANT");
    TrainingPlanEvaluationForm composite = binding(11L, 501L, "COMPOSITE");
    when(evalFormMapper.selectList(any())).thenReturn(List.of(participant, composite));
    when(evaluationFormVersionMapper.selectById(501L)).thenReturn(formVersion(501L, 701L, 2));
    when(evaluationFormVersionMapper.selectById(502L)).thenReturn(formVersion(502L, 702L, 3));
    when(evaluationFormMapper.selectById(701L)).thenReturn(form(701L, "综合素质评价表"));
    when(evaluationFormMapper.selectById(702L)).thenReturn(form(702L, "参训人员评价表"));

    TrainingPlanVO result = service.get(100L);

    assertThat(result.getEvaluationForms()).extracting("planEvaluationFormId")
      .containsExactly(11L, 12L);
    assertThat(result.getEvaluationForms().getFirst())
      .satisfies(summary -> {
        assertThat(summary.getTrainingPlanId()).isEqualTo(100L);
        assertThat(summary.getFormVersionId()).isEqualTo(501L);
        assertThat(summary.getEvaluationFormId()).isEqualTo(701L);
        assertThat(summary.getFormName()).isEqualTo("综合素质评价表");
        assertThat(summary.getVersionNo()).isEqualTo(2);
        assertThat(summary.getPublishedStatus()).isEqualTo("PUBLISHED");
        assertThat(summary.getUsageType()).isEqualTo("COMPOSITE");
        assertThat(summary.getIsRequired()).isEqualTo(1);
        assertThat(summary.getStatus()).isEqualTo(1);
      });
  }

  @Test
  @DisplayName("get：绑定版本异常缺失时返回 500/8132")
  void get_missingBoundVersion_throwsDataIntegrityError() {
    when(planMapper.selectById(100L)).thenReturn(draftPlan());
    when(stageMapper.selectList(any())).thenReturn(List.of());
    when(evalFormMapper.selectList(any())).thenReturn(List.of(binding(11L, 501L, "COMPOSITE")));
    when(evaluationFormVersionMapper.selectById(501L)).thenReturn(null);

    assertThatThrownBy(() -> service.get(100L))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("status", org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
      .hasFieldOrPropertyWithValue("code", 8132);
    verifyNoInteractions(evaluationFormMapper);
  }

  @Test
  @DisplayName("bindEvaluationForms：整体替换使用主键值软删除而非默认 delete")
  void bindEvaluationForms_replacementUsesIdValuedSoftDelete() {
    TrainingPlan plan = draftPlan();
    plan.setVersion(1);
    when(planMapper.selectById(100L)).thenReturn(plan);
    when(planMapper.updateById(plan)).thenReturn(1);
    TrainingPlanEvaluationFormBindRequest req = new TrainingPlanEvaluationFormBindRequest();
    req.setVersion(1);
    req.setForms(List.of());

    service.bindEvaluationForms(100L, req);

    verify(evalFormMapper).update(isNull(), any());
    verify(evalFormMapper, never()).delete(any());
  }

  @Test
  @DisplayName("bindEvaluationForms：同一请求重复用途返回 409/8133 且不改绑定")
  void bindEvaluationForms_duplicateUsageType_throwsWithoutMutating() {
    when(planMapper.selectById(100L)).thenReturn(draftPlan());
    TrainingPlanEvaluationFormBindRequest req = new TrainingPlanEvaluationFormBindRequest();
    req.setVersion(1);
    req.setForms(List.of(bindItem(501L, "COMPOSITE"), bindItem(502L, "COMPOSITE")));

    assertThatThrownBy(() -> service.bindEvaluationForms(100L, req))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 8133)
      .hasFieldOrPropertyWithValue("status", org.springframework.http.HttpStatus.CONFLICT)
      .hasMessageContaining("同一评价用途");
    verify(planMapper, never()).updateById(any(TrainingPlan.class));
    verify(evalFormMapper, never()).update(any(), any());
    verifyNoInteractions(evaluationFormVersionMapper);
  }

  @Test
  @DisplayName("bindEvaluationForms：跨机构表单返回 400/8134 且不改绑定")
  void bindEvaluationForms_otherOrgForm_throwsWithoutMutating() {
    when(planMapper.selectById(100L)).thenReturn(draftPlan());
    when(evaluationFormVersionMapper.selectById(501L)).thenReturn(formVersion(501L, 701L, 1));
    EvaluationForm otherOrg = form(701L, "外机构表");
    otherOrg.setOrganizationId(9L);
    otherOrg.setStatus(1);
    when(evaluationFormMapper.selectById(701L)).thenReturn(otherOrg);
    TrainingPlanEvaluationFormBindRequest req = new TrainingPlanEvaluationFormBindRequest();
    req.setVersion(1);
    req.setForms(List.of(bindItem(501L, "COMPOSITE")));

    assertThatThrownBy(() -> service.bindEvaluationForms(100L, req))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 8134)
      .hasFieldOrPropertyWithValue("status", org.springframework.http.HttpStatus.BAD_REQUEST);
    verify(planMapper, never()).updateById(any(TrainingPlan.class));
    verify(evalFormMapper, never()).update(any(), any());
  }

  @Test
  @DisplayName("bindEvaluationForms：禁用表单返回 400/8134")
  void bindEvaluationForms_disabledForm_throwsWithoutMutating() {
    when(planMapper.selectById(100L)).thenReturn(draftPlan());
    when(evaluationFormVersionMapper.selectById(501L)).thenReturn(formVersion(501L, 701L, 1));
    EvaluationForm disabled = form(701L, "禁用表");
    disabled.setOrganizationId(1L);
    disabled.setStatus(0);
    when(evaluationFormMapper.selectById(701L)).thenReturn(disabled);
    TrainingPlanEvaluationFormBindRequest req = new TrainingPlanEvaluationFormBindRequest();
    req.setVersion(1);
    req.setForms(List.of(bindItem(501L, "COMPOSITE")));

    assertThatThrownBy(() -> service.bindEvaluationForms(100L, req))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 8134);
    verify(planMapper, never()).updateById(any(TrainingPlan.class));
  }

  private TrainingPlanEvaluationFormItemRequest bindItem(Long formVersionId, String usageType) {
    TrainingPlanEvaluationFormItemRequest item = new TrainingPlanEvaluationFormItemRequest();
    item.setFormVersionId(formVersionId);
    item.setUsageType(usageType);
    item.setIsRequired(1);
    item.setStatus(1);
    return item;
  }

  private TrainingPlanEvaluationForm binding(Long bindingId, Long formVersionId, String usageType) {
    TrainingPlanEvaluationForm binding = new TrainingPlanEvaluationForm();
    binding.setPlanEvaluationFormId(bindingId);
    binding.setTrainingPlanId(100L);
    binding.setFormVersionId(formVersionId);
    binding.setUsageType(usageType);
    binding.setIsRequired(1);
    binding.setStatus(1);
    return binding;
  }

  private EvaluationFormVersion formVersion(Long versionId, Long formId, Integer versionNo) {
    EvaluationFormVersion version = new EvaluationFormVersion();
    version.setFormVersionId(versionId);
    version.setEvaluationFormId(formId);
    version.setVersionNo(versionNo);
    version.setPublishedStatus("PUBLISHED");
    return version;
  }

  private EvaluationForm form(Long formId, String formName) {
    EvaluationForm form = new EvaluationForm();
    form.setEvaluationFormId(formId);
    form.setFormName(formName);
    return form;
  }

  @Test
  @DisplayName("assignParticipant：指派参训人")
  void assignParticipant_ok() {
    TrainingPlan plan = new TrainingPlan();
    plan.setTrainingPlanId(100L);
    plan.setOrganizationId(1L);
    plan.setPublishStatus("DRAFT");
    when(planMapper.selectById(100L)).thenReturn(plan);
    when(personSelectionService.resolve(any())).thenReturn(List.of(10L));
    when(participantMapper.selectOne(any())).thenReturn(null);
    when(participantMapper.insert(any(TrainingParticipant.class))).thenAnswer(inv -> {
      TrainingParticipant p = inv.getArgument(0);
      p.setTrainingParticipantId(500L);
      return 1;
    });
    TrainingParticipantAssignRequest req = new TrainingParticipantAssignRequest();
    req.setUserId(10L);
    var vo = service.assignParticipant(100L, req);
    assertThat(vo.getUserId()).isEqualTo(10L);
    assertThat(vo.getJoinSource()).isEqualTo("ASSIGN");
    verify(personSelectionService).resolve(argThat(selection ->
                                                     "USER".equals(selection.getSelectionType()) && selection.getSubjectIds().equals(
                                                       List.of(10L))));
  }
}
