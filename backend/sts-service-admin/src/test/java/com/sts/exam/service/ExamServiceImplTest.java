package com.sts.exam.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.common.api.PageRequest;
import com.sts.common.exception.BusinessException;
import com.sts.exam.dto.*;
import com.sts.exam.entity.*;
import com.sts.exam.mapper.*;
import com.sts.paper.entity.Paper;
import com.sts.paper.entity.PaperVersion;
import com.sts.paper.mapper.PaperMapper;
import com.sts.paper.mapper.PaperVersionMapper;
import com.sts.question.entity.QuestionBank;
import com.sts.question.mapper.QuestionBankMapper;
import com.sts.exam.vo.ExamVO;
import com.sts.person.dto.PersonSelectionRequest;
import com.sts.person.service.PersonSelectionService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ExamServiceImplTest {

  private ExamMapper examMapper;
  private ExamPolicyMapper examPolicyMapper;
  private ExamCandidateMapper examCandidateMapper;
  private ExamReviewerMapper examReviewerMapper;
  private PaperVersionMapper paperVersionMapper;
  private PaperMapper paperMapper;
  private QuestionBankMapper questionBankMapper;
  private PersonSelectionService personSelectionService;
  private ExamPolicyDeleteHelper policyDeleteHelper;
  private ExamCandidateProfileSnapshotAssembler snapshotAssembler;
  private ExamServiceImpl service;
  private AssessmentExecutionService assessmentExecutionService;
  private ExamReviewerQueryMapper examReviewerQueryMapper;
  private ExamCandidateQueryMapper examCandidateQueryMapper;

  @BeforeAll
  static void initMybatisTableInfo() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, Exam.class);
  }

  @BeforeEach
  void setUp() {
    examMapper = mock(ExamMapper.class);
    examPolicyMapper = mock(ExamPolicyMapper.class);
    examCandidateMapper = mock(ExamCandidateMapper.class);
    examReviewerMapper = mock(ExamReviewerMapper.class);
    paperVersionMapper = mock(PaperVersionMapper.class);
    paperMapper = mock(PaperMapper.class);
    questionBankMapper = mock(QuestionBankMapper.class);
    personSelectionService = mock(PersonSelectionService.class);
    policyDeleteHelper = mock(ExamPolicyDeleteHelper.class);
    snapshotAssembler = mock(ExamCandidateProfileSnapshotAssembler.class);
    assessmentExecutionService = mock(AssessmentExecutionService.class);
    examReviewerQueryMapper = mock(ExamReviewerQueryMapper.class);
    examCandidateQueryMapper = mock(ExamCandidateQueryMapper.class);
    when(examMapper.selectByIdForUpdate(any())).thenAnswer(invocation -> examMapper.selectById(invocation.getArgument(0)));
    when(examMapper.updateById(any(Exam.class))).thenReturn(1);
    AssessmentExecution execution = new AssessmentExecution();
    execution.setAssessmentExecutionId(11L);
    execution.setExamId(1L);
    execution.setSourceType("STANDALONE_EXAM");
    when(assessmentExecutionService.ensureStandalone(any())).thenReturn(execution);
    when(assessmentExecutionService.findStandaloneByExamId(any())).thenReturn(execution);
    when(assessmentExecutionService.refreshStandalone(any(), any())).thenReturn(execution);
    when(assessmentExecutionService.requireStandaloneByExamId(any())).thenReturn(execution);
    when(assessmentExecutionService.requireById(any())).thenReturn(execution);
    service = new ExamServiceImpl(examMapper, examPolicyMapper, examCandidateMapper, examReviewerMapper,
                                  paperVersionMapper, paperMapper, questionBankMapper, personSelectionService, policyDeleteHelper,
                                  snapshotAssembler, examReviewerQueryMapper,
                                  examCandidateQueryMapper, assessmentExecutionService, new ObjectMapper(), mock(ExamReviewerEligibility.class));
  }

  @Test void reviewerGroupExpandsAndDeduplicatesBeforeReplacing() {
    when(examMapper.selectById(1L)).thenReturn(draftExamBoundTo(30L));
    var selection=new PersonSelectionRequest();selection.setSelectionType("GROUP");selection.setSubjectIds(List.of(5L));
    var request=new ExamReviewersReplaceRequest();request.setSelection(selection);
    when(personSelectionService.resolve(selection)).thenReturn(List.of(9L,9L));
    service.replaceReviewers(1L,request);
    verify(examReviewerMapper,times(1)).insert(argThat((ExamReviewer r)->r.getReviewerUserId().equals(9L)));
  }

  @Test void emptyReviewerGroupAndAmbiguousRequestPreserveExistingRows() {
    when(examMapper.selectById(1L)).thenReturn(draftExamBoundTo(30L));var selection=new PersonSelectionRequest();selection.setSelectionType("GROUP");selection.setSubjectIds(List.of(5L));var request=new ExamReviewersReplaceRequest();request.setSelection(selection);when(personSelectionService.resolve(selection)).thenReturn(List.of());
    assertThatThrownBy(()->service.replaceReviewers(1L,request)).hasMessageContaining("没有可用评卷人");request.setReviewerUserIds(List.of(9L));assertThatThrownBy(()->service.replaceReviewers(1L,request)).hasMessageContaining("不能同时提交");verify(examReviewerMapper,never()).delete(any());
  }

  @Test
  @DisplayName("listReviewers：草稿无执行对象返回空数组且不写库")
  void listReviewers_draftWithoutExecution_returnsEmptyWithoutCreating() {
    Exam exam = draftExamBoundTo(30L);
    when(examMapper.selectById(1L)).thenReturn(exam);
    when(assessmentExecutionService.findStandaloneByExamId(1L)).thenReturn(null);

    assertThat(service.listReviewers(1L)).isEmpty();

    verify(assessmentExecutionService, never()).ensureStandalone(any());
    verify(examReviewerQueryMapper, never()).selectReviewers(any());
  }

  @Test
  @DisplayName("listReviewers：非草稿缺执行对象继续返回 7062")
  void listReviewers_publishedWithoutExecution_throws7062() {
    Exam exam = draftExamBoundTo(30L);
    exam.setPublishStatus("PUBLISHED");
    when(examMapper.selectById(1L)).thenReturn(exam);
    when(assessmentExecutionService.findStandaloneByExamId(1L)).thenReturn(null);
    when(assessmentExecutionService.requireStandaloneByExamId(1L))
      .thenThrow(new BusinessException(HttpStatus.CONFLICT, 7062, "考试尚未生成执行对象"));

    assertThatThrownBy(() -> service.listReviewers(1L))
      .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getCode()).isEqualTo(7062));
  }

  @Test
  @DisplayName("listReviewers：考试不存在返回既有 7001")
  void listReviewers_missingExam_throws7001() {
    when(examMapper.selectById(1L)).thenReturn(null);

    assertThatThrownBy(() -> service.listReviewers(1L))
      .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getCode()).isEqualTo(7001));

    verify(assessmentExecutionService, never()).ensureStandalone(any());
    verify(examReviewerQueryMapper, never()).selectReviewers(any());
  }

  @Test
  @DisplayName("bindSelection：草稿首次非空绑定按需创建执行对象")
  void bindSelection_firstNonEmptyDraftSelection_ensuresExecution() {
    Exam exam = draftExamBoundTo(30L);
    when(examMapper.selectById(1L)).thenReturn(exam);
    when(assessmentExecutionService.findStandaloneByExamId(1L)).thenReturn(null);
    when(personSelectionService.resolve(any())).thenReturn(List.of(8L));
    when(examCandidateMapper.selectOne(any())).thenReturn(null);
    BindSelectionRequest request = new BindSelectionRequest();
    request.setSelection(new PersonSelectionRequest());

    service.bindSelection(1L, request);

    verify(assessmentExecutionService).ensureStandalone(exam);
    verify(examCandidateMapper).insert(any(ExamCandidate.class));
  }

  @Test
  @DisplayName("bindSelection：首次空选择成功且不创建执行对象")
  void bindSelection_firstEmptySelection_isNoOp() {
    Exam exam = draftExamBoundTo(30L);
    when(examMapper.selectById(1L)).thenReturn(exam);
    when(assessmentExecutionService.findStandaloneByExamId(1L)).thenReturn(null);
    when(personSelectionService.resolve(any())).thenReturn(List.of());
    BindSelectionRequest request = new BindSelectionRequest();
    request.setSelection(new PersonSelectionRequest());

    assertThat(service.bindSelection(1L, request)).isEmpty();

    verify(assessmentExecutionService, never()).ensureStandalone(any());
    verify(examCandidateMapper, never()).insert(any(ExamCandidate.class));
  }

  @Test
  @DisplayName("bindSelection：首次非空保存资源无效时不写考生")
  void bindSelection_invalidResource_doesNotWriteCandidates() {
    Exam exam = draftExamBoundTo(30L);
    when(examMapper.selectById(1L)).thenReturn(exam);
    when(assessmentExecutionService.findStandaloneByExamId(1L)).thenReturn(null);
    when(personSelectionService.resolve(any())).thenReturn(List.of(8L));
    when(assessmentExecutionService.ensureStandalone(exam))
      .thenThrow(new BusinessException(HttpStatus.BAD_REQUEST, 7005, "试卷版本未发布"));
    BindSelectionRequest request = new BindSelectionRequest();
    request.setSelection(new PersonSelectionRequest());

    assertThatThrownBy(() -> service.bindSelection(1L, request))
      .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getCode()).isEqualTo(7005));
    verify(examCandidateMapper, never()).insert(any(ExamCandidate.class));
  }

  @Test
  @DisplayName("bindSelection：非草稿缺执行对象即使选择为空也返回 7062")
  void bindSelection_emptyPublishedSelectionWithoutExecution_throws7062() {
    Exam exam = draftExamBoundTo(30L);
    exam.setPublishStatus("PUBLISHED");
    when(examMapper.selectById(1L)).thenReturn(exam);
    when(assessmentExecutionService.findStandaloneByExamId(1L)).thenReturn(null);
    when(personSelectionService.resolve(any())).thenReturn(List.of());
    when(assessmentExecutionService.requireStandaloneByExamId(1L))
      .thenThrow(new BusinessException(HttpStatus.CONFLICT, 7062, "考试尚未生成执行对象"));
    BindSelectionRequest request = new BindSelectionRequest();
    request.setSelection(new PersonSelectionRequest());

    assertThatThrownBy(() -> service.bindSelection(1L, request))
      .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getCode()).isEqualTo(7062));
  }

  @Test
  @DisplayName("bindSelection：并发插入命中唯一键后复用已落库考生")
  void bindSelection_concurrentDuplicate_recoversExistingCandidate() {
    Exam exam = draftExamBoundTo(30L);
    when(examMapper.selectById(1L)).thenReturn(exam);
    when(personSelectionService.resolve(any())).thenReturn(List.of(8L));
    ExamCandidate concurrent = new ExamCandidate();
    concurrent.setExamCandidateId(21L);
    concurrent.setAssessmentExecutionId(11L);
    concurrent.setUserId(8L);
    when(examCandidateMapper.selectOne(any())).thenReturn(null, concurrent);
    when(examCandidateMapper.insert(any(ExamCandidate.class))).thenThrow(DuplicateKeyException.class);
    BindSelectionRequest request = new BindSelectionRequest();
    request.setSelection(new PersonSelectionRequest());

    var result = service.bindSelection(1L, request);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().getExamCandidateId()).isEqualTo(21L);
  }

  @Test
  @DisplayName("replaceReviewers：草稿首次非空保存按需创建执行对象")
  void replaceReviewers_firstNonEmptyDraftRequest_ensuresExecution() {
    Exam exam = draftExamBoundTo(30L);
    when(examMapper.selectById(1L)).thenReturn(exam);
    when(assessmentExecutionService.findStandaloneByExamId(1L)).thenReturn(null);
    ExamReviewersReplaceRequest request = new ExamReviewersReplaceRequest();
    request.setReviewerUserIds(List.of(9L));

    service.replaceReviewers(1L, request);

    verify(assessmentExecutionService).ensureStandalone(exam);
    verify(examReviewerMapper).insert(argThat((ExamReviewer reviewer) -> reviewer.getReviewerUserId().equals(9L)
      && reviewer.getAssessmentExecutionId().equals(11L)));
  }

  @Test
  @DisplayName("replaceReviewers：首次非空保存资源无效时不写人员")
  void replaceReviewers_invalidResource_doesNotWriteReviewers() {
    Exam exam = draftExamBoundTo(30L);
    when(examMapper.selectById(1L)).thenReturn(exam);
    when(assessmentExecutionService.findStandaloneByExamId(1L)).thenReturn(null);
    when(assessmentExecutionService.ensureStandalone(exam))
      .thenThrow(new BusinessException(HttpStatus.BAD_REQUEST, 7005, "试卷版本未发布"));
    ExamReviewersReplaceRequest request = new ExamReviewersReplaceRequest();
    request.setReviewerUserIds(List.of(8L));

    assertThatThrownBy(() -> service.replaceReviewers(1L, request))
      .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getCode()).isEqualTo(7005));
    verify(examReviewerMapper, never()).delete(any());
    verify(examReviewerMapper, never()).insert(any(ExamReviewer.class));
  }

  @Test
  @DisplayName("replaceReviewers：首次空集合成功且不创建执行对象")
  void replaceReviewers_firstEmptyRequest_isNoOp() {
    Exam exam = draftExamBoundTo(30L);
    when(examMapper.selectById(1L)).thenReturn(exam);
    when(assessmentExecutionService.findStandaloneByExamId(1L)).thenReturn(null);
    ExamReviewersReplaceRequest request = new ExamReviewersReplaceRequest();
    request.setReviewerUserIds(List.of());

    service.replaceReviewers(1L, request);

    verify(assessmentExecutionService, never()).ensureStandalone(any());
    verify(examReviewerMapper, never()).insert(any(ExamReviewer.class));
    verify(examReviewerMapper, never()).delete(any());
  }

  @Test
  @DisplayName("replaceReviewers：非草稿缺执行对象时空保存仍返回 7062")
  void replaceReviewers_publishedWithoutExecutionEmptyRequest_throws7062() {
    Exam exam = draftExamBoundTo(30L);
    exam.setPublishStatus("PUBLISHED");
    when(examMapper.selectById(1L)).thenReturn(exam);
    when(assessmentExecutionService.findStandaloneByExamId(1L)).thenReturn(null);
    when(assessmentExecutionService.requireStandaloneByExamId(1L))
      .thenThrow(new BusinessException(HttpStatus.CONFLICT, 7062, "考试尚未生成执行对象"));
    ExamReviewersReplaceRequest request = new ExamReviewersReplaceRequest();
    request.setReviewerUserIds(List.of());

    assertThatThrownBy(() -> service.replaceReviewers(1L, request))
      .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getCode()).isEqualTo(7062));
  }

  @Test
  @DisplayName("replaceReviewers：已有执行对象时空数组清空已有阅卷人")
  void replaceReviewers_existingExecutionEmptyRequest_clearsReviewers() {
    Exam exam = draftExamBoundTo(30L);
    when(examMapper.selectById(1L)).thenReturn(exam);
    ExamReviewersReplaceRequest request = new ExamReviewersReplaceRequest();
    request.setReviewerUserIds(List.of());

    service.replaceReviewers(1L, request);

    verify(examReviewerMapper).delete(any());
    verify(examReviewerMapper, never()).insert(any(ExamReviewer.class));
  }

  @Test
  @DisplayName("publish：在原执行对象上刷新最终快照")
  void publish_refreshesStandaloneSnapshot() {
    Exam exam = draftExamBoundTo(30L);
    when(examMapper.selectById(1L)).thenReturn(exam);
    PaperVersion version = sampleVersion();
    version.setPublishedStatus("PUBLISHED");
    when(paperVersionMapper.selectById(30L)).thenReturn(version);

    service.publish(1L);

    verify(assessmentExecutionService).refreshStandalone(exam, null);
    verify(assessmentExecutionService, never()).ensureStandalone(exam);
    assertThat(exam.getPublishStatus()).isEqualTo("PUBLISHED");
  }

  @Test
  @DisplayName("publish：考试乐观锁更新失败时抛错以触发事务回滚")
  void publish_examUpdateConflict_throws7004() {
    Exam exam = draftExamBoundTo(30L);
    when(examMapper.selectById(1L)).thenReturn(exam);
    PaperVersion version = sampleVersion();
    version.setPublishedStatus("PUBLISHED");
    when(paperVersionMapper.selectById(30L)).thenReturn(version);
    when(examMapper.updateById(exam)).thenReturn(0);

    assertThatThrownBy(() -> service.publish(1L))
      .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getCode()).isEqualTo(7004));

    verify(assessmentExecutionService).refreshStandalone(exam, null);
  }

  @Test
  @DisplayName("publish：计划专属考试把覆盖项交给同一套物化")
  void publish_planScopedExam_passesExtraPolicy() {
    Exam exam = draftExamBoundTo(30L);
    exam.setExamType("ONLINE");
    exam.setPlanContentId(301L);
    when(examMapper.selectById(1L)).thenReturn(exam);
    PaperVersion version = sampleVersion();
    version.setPublishedStatus("PUBLISHED");
    when(paperVersionMapper.selectById(30L)).thenReturn(version);
    java.util.Map<String, Object> extra = java.util.Map.of(
      "assessmentKind", "QUIZ", "trainingPlanContentId", 301L);

    service.publish(1L, extra);

    verify(assessmentExecutionService).refreshStandalone(exam, extra);
    assertThat(exam.getPublishStatus()).isEqualTo("PUBLISHED");
  }

  @Test
  @DisplayName("delete：草稿删除先清理未投入执行的人员归属")
  void delete_cleansDraftExecutionLifecycle() {
    Exam exam = draftExamBoundTo(30L);
    when(examMapper.selectById(1L)).thenReturn(exam);
    when(examPolicyMapper.selectOne(any())).thenReturn(null);

    ExamServiceImpl deletingService = spy(service);
    doReturn(true).when(deletingService).removeById(1L);

    deletingService.delete(1L);

    verify(assessmentExecutionService).deleteStandaloneIfUnused(1L);
  }

  @Test
  @DisplayName("bindSelection：选人后写入具体考生且来源为 ASSIGN")
  void bindSelection_createsConcreteCandidates() {
    Exam exam = new Exam();
    exam.setExamId(1L);
    exam.setOrganizationId(1L);
    when(examMapper.selectById(1L)).thenReturn(exam);
    when(personSelectionService.resolve(any())).thenReturn(List.of(8L));
    when(examCandidateMapper.selectOne(any())).thenReturn(null);

    BindSelectionRequest request = new BindSelectionRequest();
    PersonSelectionRequest selection = new PersonSelectionRequest();
    selection.setSelectionType("USER");
    selection.setSubjectIds(List.of(8L));
    request.setSelection(selection);

    service.bindSelection(1L, request);

    verify(examCandidateMapper).insert(argThat((ExamCandidate candidate) ->
                                                 candidate.getUserId().equals(8L) && candidate.getAssessmentExecutionId().equals(
                                                   11L)
                                                   && "ASSIGN".equals(candidate.getAssignmentSource())));
  }

  @Test
  @DisplayName("bindSelection：人员唯一归属于独立考试执行")
  void bindSelection_usesStandaloneExecution() {
    Exam exam = new Exam();
    exam.setExamId(1L);
    when(examMapper.selectById(1L)).thenReturn(exam);
    when(personSelectionService.resolve(any())).thenReturn(List.of(8L));
    when(examCandidateMapper.selectOne(any())).thenReturn(null);

    BindSelectionRequest request = new BindSelectionRequest();
    request.setSelection(new PersonSelectionRequest());

    service.bindSelection(1L, request);

    verify(examCandidateMapper).insert(argThat((ExamCandidate candidate) ->
                                                 candidate.getAssessmentExecutionId().equals(11L) && "ASSIGN".equals(
                                                   candidate.getAssignmentSource())));
  }

  @Test
  @DisplayName("get：详情装配考试策略")
  void get_assemblesPolicy() {
    Exam exam = new Exam();
    exam.setExamId(1L);
    exam.setExamName("考试1");
    when(examMapper.selectById(1L)).thenReturn(exam);
    ExamPolicy policy = new ExamPolicy();
    policy.setExamPolicyId(9L);
    policy.setExamId(1L);
    policy.setIsRetakeEnabled(1);
    policy.setRetakeMaxCount(2);
    policy.setRetakeScoreMode("HIGHEST");
    policy.setIsScoreVisible(0);
    when(examPolicyMapper.selectOne(any())).thenReturn(policy);

    var vo = service.get(1L);

    assertThat(vo.getPolicy()).isNotNull();
    assertThat(vo.getPolicy().getIsRetakeEnabled()).isEqualTo(1);
    assertThat(vo.getPolicy().getRetakeMaxCount()).isEqualTo(2);
    assertThat(vo.getPolicy().getRetakeScoreMode()).isEqualTo("HIGHEST");
    assertThat(vo.getPolicy().getIsScoreVisible()).isZero();
  }

  @Test
  @DisplayName("get：未配置策略时 policy 为 null，不报错")
  void get_withoutPolicy_returnsNull() {
    Exam exam = new Exam();
    exam.setExamId(1L);
    when(examMapper.selectById(1L)).thenReturn(exam);
    when(examPolicyMapper.selectOne(any())).thenReturn(null);

    assertThat(service.get(1L).getPolicy()).isNull();
  }

  @Test
  @DisplayName("page：列表不装配策略，避免 N+1")
  void page_doesNotAssemblePolicy() {
    Exam exam = new Exam();
    exam.setExamId(1L);
    IPage<Exam> page = new Page<>(1, 10);
    page.setRecords(List.of(exam));
    page.setTotal(1);
    when(examMapper.selectPage(any(), any())).thenReturn((Page<Exam>) page);

    PageRequest<ExamQuery> request = new PageRequest<>();
    request.setPageNum(1);
    request.setPageSize(10);
    var result = service.page(request);

    assertThat(result.list()).hasSize(1);
    assertThat(result.list().getFirst().getPolicy()).isNull();
    verify(examPolicyMapper, never()).selectOne(any());
  }

  @Test
  @DisplayName("page：默认包含培训生成考试并暴露 planContentId")
  void page_includesTrainingExamPlanContentId() {
    Exam independent = new Exam();
    independent.setExamId(1L);
    Exam training = new Exam();
    training.setExamId(2L);
    training.setPlanContentId(301L);
    IPage<Exam> page = new Page<>(1, 10);
    page.setRecords(List.of(independent, training));
    page.setTotal(2);
    when(examMapper.selectPage(any(), any())).thenReturn((Page<Exam>) page);

    var result = service.page(new PageRequest<>(1, 10, new ExamQuery()));

    assertThat(result.total()).isEqualTo(2);
    assertThat(result.list().getFirst().getPlanContentId()).isNull();
    assertThat(result.list().get(1).getPlanContentId()).isEqualTo(301L);
  }

  @Test
  @DisplayName("page：可按 planContentId 过滤")
  void page_filtersByPlanContentId() {
    IPage<Exam> page = new Page<>(1, 10);
    page.setRecords(List.of());
    page.setTotal(0);
    when(examMapper.selectPage(any(), any())).thenReturn((Page<Exam>) page);
    ExamQuery query = new ExamQuery();
    query.setPlanContentId(301L);

    service.page(new PageRequest<>(1, 10, query));

    ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Exam>> captor =
      ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
    verify(examMapper).selectPage(any(), captor.capture());
    assertThat(captor.getValue().getSqlSegment()).containsIgnoringCase("plan_content_id");
    assertThat(captor.getValue().getParamNameValuePairs()).containsValue(301L);
  }

  @Test
  @DisplayName("get：详情返回 planContentId")
  void get_exposesPlanContentId() {
    Exam exam = new Exam();
    exam.setExamId(1L);
    exam.setPlanContentId(301L);
    when(examMapper.selectById(1L)).thenReturn(exam);
    when(examPolicyMapper.selectOne(any())).thenReturn(null);

    assertThat(service.get(1L).getPlanContentId()).isEqualTo(301L);
  }

  @Test
  @DisplayName("create：绑卷且省略规则字段时从试卷版本带出")
  void create_bindsPaperAndInheritsRules() {
    stubPaperVersion(sampleVersion());
    stubCreateInsert();

    ExamCreateRequest request = baseCreateRequest();
    request.setPaperVersionId(30L);

    service.create(request);

    Exam saved = capturedInsert();
    assertThat(saved.getDurationSeconds()).isEqualTo(3600);
    assertThat(saved.getPassRatio()).isEqualByComparingTo("0.60");
    assertThat(saved.getQuestionOrderMode()).isEqualTo("IMPORT");
    assertThat(saved.getMultiSelectScoring()).isEqualTo("ALL_OR_NOTHING");
    assertThat(saved.getPassScore()).isEqualByComparingTo("60.00");
  }

  @Test
  @DisplayName("create：绑卷同时提交时长时覆盖，其余从试卷带出")
  void create_overridesDurationOnly() {
    stubPaperVersion(sampleVersion());
    stubCreateInsert();

    ExamCreateRequest request = baseCreateRequest();
    request.setPaperVersionId(30L);
    request.setDurationSeconds(1800);

    service.create(request);

    Exam saved = capturedInsert();
    assertThat(saved.getDurationSeconds()).isEqualTo(1800);
    assertThat(saved.getPassRatio()).isEqualByComparingTo("0.60");
    assertThat(saved.getQuestionOrderMode()).isEqualTo("IMPORT");
    assertThat(saved.getMultiSelectScoring()).isEqualTo("ALL_OR_NOTHING");
  }

  @Test
  @DisplayName("create：试卷版本不存在返回 7005 且不落库")
  void create_missingPaperVersion_throws7005() {
    when(paperVersionMapper.selectById(99L)).thenReturn(null);

    ExamCreateRequest request = baseCreateRequest();
    request.setPaperVersionId(99L);

    assertThatThrownBy(() -> service.create(request))
      .isInstanceOfSatisfying(BusinessException.class, ex -> {
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getCode()).isEqualTo(7005);
      });
    verify(examMapper, never()).insert(any(Exam.class));
  }

  @Test
  @DisplayName("create：无试卷且省略时长返回 1001 且不落库")
  void create_withoutPaperMissingDuration_throws1001() {
    ExamCreateRequest request = baseCreateRequest();
    request.setPassRatio(new BigDecimal("0.6"));
    request.setPassScore(new BigDecimal("55"));
    request.setQuestionOrderMode("IMPORT");
    request.setMultiSelectScoring("ALL_OR_NOTHING");

    assertThatThrownBy(() -> service.create(request))
      .isInstanceOfSatisfying(BusinessException.class, ex -> {
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getCode()).isEqualTo(1001);
      });
    verify(examMapper, never()).insert(any(Exam.class));
    verify(paperVersionMapper, never()).selectById(any());
  }

  @Test
  @DisplayName("create：无试卷时保留手填及格分")
  void create_withoutPaper_keepsPassScore() {
    stubCreateInsert();
    ExamCreateRequest request = baseCreateRequest();
    request.setDurationSeconds(2400);
    request.setPassRatio(new BigDecimal("0.5"));
    request.setPassScore(new BigDecimal("55"));
    request.setQuestionOrderMode("TYPE");
    request.setMultiSelectScoring("PARTIAL");

    service.create(request);

    Exam saved = capturedInsert();
    assertThat(saved.getPassScore()).isEqualByComparingTo("55");
    assertThat(saved.getDurationSeconds()).isEqualTo(2400);
  }

  @Test
  @DisplayName("create：有试卷时提交及格分则作为本场覆盖")
  void create_withPaper_overridesPassScore() {
    stubPaperVersion(sampleVersion());
    stubCreateInsert();

    ExamCreateRequest request = baseCreateRequest();
    request.setPaperVersionId(30L);
    request.setPassRatio(new BigDecimal("0.6"));
    request.setPassScore(new BigDecimal("1"));

    service.create(request);

    assertThat(capturedInsert().getPassScore()).isEqualByComparingTo("1");
  }

  @Test
  @DisplayName("create：未提供封面时带出试卷封面，不带出分类")
  void create_copiesPaperCoverNotCategory() {
    PaperVersion version = sampleVersion();
    stubPaperVersion(version);
    Paper paper = new Paper();
    paper.setPaperId(8L);
    paper.setCoverFileId(77L);
    paper.setPaperCategoryId(5L);
    when(paperMapper.selectById(8L)).thenReturn(paper);
    stubCreateInsert();

    ExamCreateRequest request = baseCreateRequest();
    request.setPaperVersionId(30L);
    request.setExamCategoryId(3L);

    service.create(request);

    Exam saved = capturedInsert();
    assertThat(saved.getCoverFileId()).isEqualTo(77L);
    assertThat(saved.getExamCategoryId()).isEqualTo(3L);
    assertThat(saved.getExamCode()).isEqualTo("E1");
    assertThat(saved.getExamName()).isEqualTo("安全考试");
  }

  @Test
  @DisplayName("update：换绑且省略规则字段时按新版本重填")
  void update_rebindWithoutRules_refillsFromNewVersion() {
    Exam exam = draftExamBoundTo(30L);
    exam.setDurationSeconds(1800);
    when(examMapper.selectById(1L)).thenReturn(exam);
    PaperVersion next = sampleVersion();
    next.setPaperVersionId(40L);
    next.setDurationSeconds(2700);
    next.setPassRatio(new BigDecimal("0.80"));
    next.setQuestionOrderMode("RANDOM");
    next.setMultiSelectScoring("PARTIAL");
    next.setTotalScore(new BigDecimal("50"));
    when(paperVersionMapper.selectById(40L)).thenReturn(next);

    ExamUpdateRequest request = baseUpdateRequest();
    request.setPaperVersionId(40L);

    service.update(1L, request);

    ArgumentCaptor<Exam> captor = ArgumentCaptor.forClass(Exam.class);
    verify(examMapper).updateById(captor.capture());
    Exam saved = captor.getValue();
    assertThat(saved.getDurationSeconds()).isEqualTo(2700);
    assertThat(saved.getPassRatio()).isEqualByComparingTo("0.80");
    assertThat(saved.getQuestionOrderMode()).isEqualTo("RANDOM");
    assertThat(saved.getMultiSelectScoring()).isEqualTo("PARTIAL");
    assertThat(saved.getPassScore()).isEqualByComparingTo("40.00");
  }

  @Test
  @DisplayName("update：换绑且提交时长时覆盖，其余从新版本带出")
  void update_rebindOverridesDuration() {
    Exam exam = draftExamBoundTo(30L);
    when(examMapper.selectById(1L)).thenReturn(exam);
    PaperVersion next = sampleVersion();
    next.setPaperVersionId(40L);
    next.setDurationSeconds(2700);
    when(paperVersionMapper.selectById(40L)).thenReturn(next);

    ExamUpdateRequest request = baseUpdateRequest();
    request.setPaperVersionId(40L);
    request.setDurationSeconds(900);

    service.update(1L, request);

    ArgumentCaptor<Exam> captor = ArgumentCaptor.forClass(Exam.class);
    verify(examMapper).updateById(captor.capture());
    assertThat(captor.getValue().getDurationSeconds()).isEqualTo(900);
    assertThat(captor.getValue().getQuestionOrderMode()).isEqualTo("IMPORT");
  }

  @Test
  @DisplayName("update：未换绑省略时长时保留原覆盖值")
  void update_samePaperOmitsDuration_keepsOverride() {
    Exam exam = draftExamBoundTo(30L);
    exam.setDurationSeconds(1800);
    exam.setPassRatio(new BigDecimal("0.60"));
    exam.setPassScore(new BigDecimal("60.00"));
    when(examMapper.selectById(1L)).thenReturn(exam);
    when(paperVersionMapper.selectById(30L)).thenReturn(sampleVersion());

    ExamUpdateRequest request = baseUpdateRequest();
    request.setPaperVersionId(30L);
    request.setExamName("改名");

    service.update(1L, request);

    ArgumentCaptor<Exam> captor = ArgumentCaptor.forClass(Exam.class);
    verify(examMapper).updateById(captor.capture());
    Exam saved = captor.getValue();
    assertThat(saved.getDurationSeconds()).isEqualTo(1800);
    assertThat(saved.getPassScore()).isEqualByComparingTo("60.00");
    assertThat(saved.getExamName()).isEqualTo("改名");
  }

  @Test
  @DisplayName("update：未换绑提交新比例且省略及格分时按试卷总分重算")
  void update_samePaperNewRatio_recomputesPassScoreWhenPassScoreOmitted() {
    Exam exam = draftExamBoundTo(30L);
    exam.setPassRatio(new BigDecimal("0.60"));
    exam.setPassScore(new BigDecimal("60.00"));
    when(examMapper.selectById(1L)).thenReturn(exam);
    when(paperVersionMapper.selectById(30L)).thenReturn(sampleVersion());

    ExamUpdateRequest request = baseUpdateRequest();
    request.setPaperVersionId(30L);
    request.setPassRatio(new BigDecimal("0.50"));

    service.update(1L, request);

    ArgumentCaptor<Exam> captor = ArgumentCaptor.forClass(Exam.class);
    verify(examMapper).updateById(captor.capture());
    assertThat(captor.getValue().getPassScore()).isEqualByComparingTo("50.00");
  }

  @Test
  @DisplayName("update：未换绑提交及格分则作为本场覆盖")
  void update_samePaper_overridesPassScore() {
    Exam exam = draftExamBoundTo(30L);
    exam.setPassRatio(new BigDecimal("0.60"));
    exam.setPassScore(new BigDecimal("60.00"));
    when(examMapper.selectById(1L)).thenReturn(exam);

    ExamUpdateRequest request = baseUpdateRequest();
    request.setPaperVersionId(30L);
    request.setPassScore(new BigDecimal("55"));

    service.update(1L, request);

    ArgumentCaptor<Exam> captor = ArgumentCaptor.forClass(Exam.class);
    verify(examMapper).updateById(captor.capture());
    assertThat(captor.getValue().getPassScore()).isEqualByComparingTo("55");
  }

  @Test
  @DisplayName("update：换绑试卷版本不存在返回 7005 且不更新")
  void update_missingPaperVersion_throws7005() {
    when(examMapper.selectById(1L)).thenReturn(draftExamBoundTo(30L));
    when(paperVersionMapper.selectById(99L)).thenReturn(null);

    ExamUpdateRequest request = baseUpdateRequest();
    request.setPaperVersionId(99L);

    assertThatThrownBy(() -> service.update(1L, request))
      .isInstanceOfSatisfying(BusinessException.class, ex -> {
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getCode()).isEqualTo(7005);
      });
    verify(examMapper, never()).updateById(any(Exam.class));
  }

  @Test
  @DisplayName("update：清空试卷且省略时长返回 1001")
  void update_clearPaperMissingDuration_throws1001() {
    when(examMapper.selectById(1L)).thenReturn(draftExamBoundTo(30L));

    ExamUpdateRequest request = baseUpdateRequest();
    request.setPaperVersionId(null);
    request.setPassRatio(new BigDecimal("0.6"));
    request.setPassScore(new BigDecimal("55"));
    request.setQuestionOrderMode("IMPORT");
    request.setMultiSelectScoring("ALL_OR_NOTHING");

    assertThatThrownBy(() -> service.update(1L, request))
      .isInstanceOfSatisfying(BusinessException.class, ex -> {
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getCode()).isEqualTo(1001);
      });
    verify(examMapper, never()).updateById(any(Exam.class));
  }

  private void stubPaperVersion(PaperVersion version) {
    when(paperVersionMapper.selectById(version.getPaperVersionId())).thenReturn(version);
  }

  private void stubCreateInsert() {
    when(examMapper.insert(any(Exam.class))).thenAnswer(inv -> {
      Exam exam = inv.getArgument(0);
      exam.setExamId(10L);
      return 1;
    });
    when(examPolicyMapper.selectOne(any())).thenReturn(null);
  }

  private Exam capturedInsert() {
    ArgumentCaptor<Exam> captor = ArgumentCaptor.forClass(Exam.class);
    verify(examMapper).insert(captor.capture());
    return captor.getValue();
  }

  private PaperVersion sampleVersion() {
    PaperVersion version = new PaperVersion();
    version.setPaperVersionId(30L);
    version.setPaperId(8L);
    version.setDurationSeconds(3600);
    version.setPassRatio(new BigDecimal("0.60"));
    version.setPassScore(new BigDecimal("60.00"));
    version.setTotalScore(new BigDecimal("100"));
    version.setQuestionOrderMode("IMPORT");
    version.setMultiSelectScoring("ALL_OR_NOTHING");
    return version;
  }

  @Test
  @DisplayName("get：返回试卷名称、版本号和题库名称，不暴露资源 ID 作为展示名称")
  void get_populatesReadableResourceNames() {
    Exam exam = draftExamBoundTo(30L);
    exam.setQuestionBankId(70L);
    when(examMapper.selectById(1L)).thenReturn(exam);
    PaperVersion paperVersion = sampleVersion();
    paperVersion.setVersionNo(3);
    when(paperVersionMapper.selectById(30L)).thenReturn(paperVersion);
    Paper paper = new Paper();
    paper.setPaperName("安全生产基础试卷");
    when(paperMapper.selectById(8L)).thenReturn(paper);
    QuestionBank questionBank = new QuestionBank();
    questionBank.setBankName("安全生产基础题库");
    when(questionBankMapper.selectById(70L)).thenReturn(questionBank);

    ExamVO result = service.get(1L);

    assertThat(result.getPaperName()).isEqualTo("安全生产基础试卷");
    assertThat(result.getPaperVersionNo()).isEqualTo(3);
    assertThat(result.getQuestionBankName()).isEqualTo("安全生产基础题库");
  }

  @Test
  @DisplayName("create：题库练习落库抽题规则 JSON")
  void create_bankPractice_persistsDrawRules() {
    stubCreateInsert();
    ExamCreateRequest request = bankPracticeCreateRequest(new BigDecimal("20"));
    request.setDrawRules(List.of(drawRule("SINGLE", 3, "4"), drawRule("MULTIPLE", 2, "4")));

    service.create(request);

    assertThat(capturedInsert().getDrawRuleJson()).contains("SINGLE").contains("\"drawCount\":3");
  }

  @Test
  @DisplayName("create：题库练习无需提交及格比例与及格分")
  void create_bankPractice_doesNotRequirePassingRules() {
    stubCreateInsert();
    ExamCreateRequest request = bankPracticeCreateRequest(null);
    request.setPassRatio(null);
    request.setDrawRules(List.of(drawRule("SINGLE", 3, "1")));

    service.create(request);

    assertThat(capturedInsert().getPassRatio()).isEqualByComparingTo("0");
    assertThat(capturedInsert().getPassScore()).isEqualByComparingTo("0");
  }

  @Test
  @DisplayName("create：题库练习不按及格分与抽题分值计算，仍可保存规则")
  void create_bankPracticePassScoreAboveDrawTotal_persistsRules() {
    ExamCreateRequest request = bankPracticeCreateRequest(new BigDecimal("60"));
    request.setDrawRules(List.of(drawRule("SINGLE", 5, "4")));

    stubCreateInsert();
    service.create(request);

    verify(examMapper).insert(any(Exam.class));
  }

  @Test
  @DisplayName("create：非题库练习提交的抽题规则被忽略")
  void create_otherTypes_ignoreDrawRules() {
    stubCreateInsert();
    ExamCreateRequest request = baseCreateRequest();
    request.setDurationSeconds(600);
    request.setPassRatio(new BigDecimal("0.6"));
    request.setPassScore(new BigDecimal("60"));
    request.setQuestionOrderMode("IMPORT");
    request.setMultiSelectScoring("ALL_OR_NOTHING");
    request.setDrawRules(List.of(drawRule("SINGLE", 3, "4")));

    service.create(request);

    assertThat(capturedInsert().getDrawRuleJson()).isNull();
  }

  @Test
  @DisplayName("publish：题库练习把考试抽题规则注入执行快照")
  void publish_bankPractice_injectsDrawRules() {
    Exam exam = draftBankPractice();
    when(examMapper.selectById(1L)).thenReturn(exam);

    service.publish(1L);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<java.util.Map<String, Object>> captor = ArgumentCaptor.forClass(java.util.Map.class);
    verify(assessmentExecutionService).refreshStandalone(org.mockito.ArgumentMatchers.eq(exam), captor.capture());
    assertThat(captor.getValue()).containsKey("drawRules");
    assertThat(exam.getPublishStatus()).isEqualTo("PUBLISHED");
  }

  @Test
  @DisplayName("publish：题库练习未配置抽题规则返回 7063")
  void publish_bankPracticeWithoutDrawRules_throws7063() {
    Exam exam = draftBankPractice();
    exam.setDrawRuleJson(null);
    when(examMapper.selectById(1L)).thenReturn(exam);

    assertThatThrownBy(() -> service.publish(1L))
      .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getCode()).isEqualTo(7063));
    assertThat(exam.getPublishStatus()).isEqualTo("DRAFT");
    verify(assessmentExecutionService, never()).refreshStandalone(any(), any());
  }

  private ExamCreateRequest baseCreateRequest() {
    ExamCreateRequest request = new ExamCreateRequest();
    request.setExamCode("E1");
    request.setExamName("安全考试");
    request.setExamType("ONLINE");
    request.setCredit(new BigDecimal("1"));
    request.setStatus(1);
    return request;
  }

  private ExamUpdateRequest baseUpdateRequest() {
    ExamUpdateRequest request = new ExamUpdateRequest();
    request.setExamName("安全考试");
    request.setCredit(new BigDecimal("1"));
    request.setStatus(1);
    request.setVersion(0);
    return request;
  }

  /** 题库练习创建请求：无试卷，仅时长、题序和多选题计分是生效规则。 */
  private ExamCreateRequest bankPracticeCreateRequest(BigDecimal passScore) {
    ExamCreateRequest request = baseCreateRequest();
    request.setExamType("BANK_PRACTICE");
    request.setQuestionBankId(70L);
    request.setDurationSeconds(600);
    request.setPassRatio(new BigDecimal("0.6"));
    request.setPassScore(passScore);
    request.setQuestionOrderMode("RANDOM");
    request.setMultiSelectScoring("ALL_OR_NOTHING");
    return request;
  }

  private ExamDrawRuleRequest drawRule(String questionType, int drawCount, String scorePerQuestion) {
    ExamDrawRuleRequest rule = new ExamDrawRuleRequest();
    rule.setQuestionType(questionType);
    rule.setDrawCount(drawCount);
    rule.setScorePerQuestion(new BigDecimal(scorePerQuestion));
    rule.setSortOrder(1);
    return rule;
  }

  /** 已配置 1 条「5 题 × 4 分」抽题规则的题库练习草稿。 */
  private Exam draftBankPractice() {
    Exam exam = draftExamBoundTo(null);
    exam.setExamType("BANK_PRACTICE");
    exam.setQuestionBankId(70L);
    exam.setDrawRuleJson("[{\"questionType\":\"SINGLE\",\"drawCount\":5,\"scorePerQuestion\":4,\"sortOrder\":1}]");
    return exam;
  }

  private Exam draftExamBoundTo(Long paperVersionId) {
    Exam exam = new Exam();
    exam.setExamId(1L);
    exam.setExamName("安全考试");
    exam.setPublishStatus("DRAFT");
    exam.setPaperVersionId(paperVersionId);
    exam.setDurationSeconds(3600);
    exam.setPassRatio(new BigDecimal("0.60"));
    exam.setPassScore(new BigDecimal("60.00"));
    exam.setQuestionOrderMode("IMPORT");
    exam.setMultiSelectScoring("ALL_OR_NOTHING");
    exam.setCredit(new BigDecimal("1"));
    exam.setStatus(1);
    exam.setVersion(0);
    exam.setCoverFileId(11L);
    return exam;
  }
}
