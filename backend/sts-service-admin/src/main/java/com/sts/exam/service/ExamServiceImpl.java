package com.sts.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.common.exception.BusinessException;
import com.sts.common.service.BaseServiceImpl;
import com.sts.exam.dto.*;
import com.sts.exam.entity.*;
import com.sts.exam.mapper.*;
import com.sts.exam.vo.ExamCandidateVO;
import com.sts.exam.vo.ExamDrawRuleVO;
import com.sts.exam.vo.ExamPolicyVO;
import com.sts.exam.vo.ExamReviewerVO;
import com.sts.exam.vo.ExamVO;
import com.sts.paper.entity.Paper;
import com.sts.paper.entity.PaperVersion;
import com.sts.paper.mapper.PaperMapper;
import com.sts.paper.mapper.PaperVersionMapper;
import com.sts.question.entity.QuestionBank;
import com.sts.question.mapper.QuestionBankMapper;
import com.sts.person.service.PersonSelectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExamServiceImpl
  extends BaseServiceImpl<ExamMapper, Exam>
  implements ExamService {

  private final ExamMapper examMapper;
  private final ExamPolicyMapper examPolicyMapper;
  private final ExamCandidateMapper examCandidateMapper;
  private final ExamReviewerMapper examReviewerMapper;
  private final PaperVersionMapper paperVersionMapper;
  private final PaperMapper paperMapper;
  private final QuestionBankMapper questionBankMapper;
  private final PersonSelectionService personSelectionService;
  private final ExamPolicyDeleteHelper policyDeleteHelper;
  private final ExamCandidateProfileSnapshotAssembler profileSnapshotAssembler;
  private final ExamReviewerQueryMapper examReviewerQueryMapper;
  private final ExamCandidateQueryMapper examCandidateQueryMapper;
  private final AssessmentExecutionService assessmentExecutionService;
  private final ObjectMapper objectMapper;
  /** 统一评卷资格，不能仅依赖前端候选筛选。 */
  private final ExamReviewerEligibility reviewerEligibility;

  @Override
  public PageResult<ExamVO> page(PageRequest<ExamQuery> request) {
    ExamQuery query = request.getQuery();
    LambdaQueryWrapper<Exam> wrapper = new LambdaQueryWrapper<>();
    if (query != null) {
      wrapper
        .eq(StringUtils.hasText(query.getExamType()), Exam::getExamType, query.getExamType())
        .eq(StringUtils.hasText(query.getPublishStatus()), Exam::getPublishStatus, query.getPublishStatus())
        .like(StringUtils.hasText(query.getExamName()), Exam::getExamName, query.getExamName())
        .eq(query.getStatus() != null, Exam::getStatus, query.getStatus())
        .eq(query.getPlanContentId() != null, Exam::getPlanContentId, query.getPlanContentId());
    }
    wrapper.orderByDesc(Exam::getCreateTime);
    Page<Exam> page = examMapper.selectPage(new Page<>(request.getPageNum(), request.getPageSize()), wrapper);
    return new PageResult<>(page.getRecords().stream().map(this::toVO).toList(), page.getTotal());
  }

  @Override
  public ExamVO get(Long examId) {
    return toVO(requireExam(examId), true);
  }

  @Override
  @Transactional
  public Long create(ExamCreateRequest request) {
    Exam exam = new Exam();
    applyCreate(exam, request);
    exam.setPublishStatus("DRAFT");
    try {
      examMapper.insert(exam);
    } catch (DuplicateKeyException ex) {
      throw new BusinessException(HttpStatus.CONFLICT, 7002, "同机构下考试编码已存在");
    }
    upsertPolicy(exam.getExamId(), request.getPolicy());
    return exam.getExamId();
  }

  @Override
  @Transactional
  public void update(Long examId, ExamUpdateRequest request) {
    Exam exam = requireExamForUpdate(examId);
    if (!"DRAFT".equals(exam.getPublishStatus())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 7003, "仅草稿考试可编辑");
    }
    applyUpdateRules(exam, request);
    exam.setVersion(request.getVersion());
    exam.setExamName(request.getExamName().trim());
    exam.setExamCategoryId(request.getExamCategoryId());
    exam.setCoverFileId(request.getCoverFileId());
    exam.setPaperVersionId(request.getPaperVersionId());
    exam.setQuestionBankId(request.getQuestionBankId());
    exam.setResponsibleUserId(request.getResponsibleUserId());
    exam.setOpenStartTime(request.getOpenStartTime());
    exam.setOpenEndTime(request.getOpenEndTime());
    exam.setCredit(request.getCredit());
    exam.setIsNoticeEnabled(request.getIsNoticeEnabled());
    exam.setNoticeContent(request.getNoticeContent());
    exam.setStatus(request.getStatus());
    if (request.getDrawRules() != null) {
      exam.setDrawRuleJson(writeDrawRules(request.getDrawRules(), exam.getExamType()));
    }
    examMapper.updateById(exam);
    if (request.getPolicy() != null) {
      upsertPolicy(examId, request.getPolicy());
    }
  }

  @Override
  @Transactional
  public void delete(Long examId) {
    Exam exam = requireExamForUpdate(examId);
    if (!"DRAFT".equals(exam.getPublishStatus())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 7003, "仅草稿考试可删除");
    }
    assessmentExecutionService.deleteStandaloneIfUnused(examId);
    ExamPolicy policy = examPolicyMapper.selectOne(new LambdaQueryWrapper<ExamPolicy>()
      .eq(ExamPolicy::getExamId, examId));
    if (policy != null) {
      policyDeleteHelper.removeById(policy.getExamPolicyId());
    }
    this.removeById(exam.getExamId());
  }

  @Override
  @Transactional
  public void publish(Long examId) {
    publish(examId, null);
  }

  @Override
  @Transactional
  public void publish(Long examId, java.util.Map<String, Object> extraPolicy) {
    Exam exam = requireExamForUpdate(examId);
    if (!"DRAFT".equals(exam.getPublishStatus()) && !"PUBLISHED".equals(exam.getPublishStatus())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 7004, "当前状态不可发布");
    }
    if ("ONLINE".equals(exam.getExamType()) || "SPECIAL_PRACTICE".equals(exam.getExamType())) {
      if (exam.getPaperVersionId() == null) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, 7005, "线上考试必须绑定试卷版本");
      }
      PaperVersion pv = paperVersionMapper.selectById(exam.getPaperVersionId());
      if (pv == null || !"PUBLISHED".equals(pv.getPublishedStatus())) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, 7005, "试卷版本未发布");
      }
    }
    if ("DRAFT".equals(exam.getPublishStatus())) {
      assessmentExecutionService.refreshStandalone(exam, mergeStandaloneDrawRules(exam, extraPolicy));
    } else {
      assessmentExecutionService.requireStandaloneByExamId(examId);
    }
    exam.setPublishStatus("PUBLISHED");
    exam.setPublishedTime(LocalDateTime.now());
    if (examMapper.updateById(exam) != 1) {
      throw new BusinessException(HttpStatus.CONFLICT, 7004, "考试已被修改，请刷新后重试");
    }
  }

  @Override
  @Transactional
  public List<ExamCandidateVO> bindSelection(Long examId, BindSelectionRequest request) {
    Exam exam = requireExamForUpdate(examId);
    List<Long> userIds = personSelectionService.resolve(request.getSelection());
    var execution = assessmentExecutionService.findStandaloneByExamId(examId);
    if (userIds.isEmpty()) {
      if (execution == null && !"DRAFT".equals(exam.getPublishStatus())) {
        assessmentExecutionService.requireStandaloneByExamId(examId);
      }
      return List.of();
    }
    if (execution == null) execution = participantExecution(exam);
    Long assessmentExecutionId = execution.getAssessmentExecutionId();
    List<ExamCandidateVO> result = new ArrayList<>();
    LocalDateTime now = LocalDateTime.now();
    for (Long userId : userIds) {
      ExamCandidate existing = examCandidateMapper.selectOne(new LambdaQueryWrapper<ExamCandidate>()
                                                               .eq(ExamCandidate::getAssessmentExecutionId,
                                                                   assessmentExecutionId)
                                                               .eq(ExamCandidate::getUserId, userId));
      if (existing != null) {
        result.add(toCandidateVO(existing));
        continue;
      }
      ExamCandidate candidate = new ExamCandidate();
      candidate.setAssessmentExecutionId(assessmentExecutionId);
      candidate.setUserId(userId);
      candidate.setAssignmentSource("ASSIGN");
      candidate.setCandidateProfileSnapshotJson(
        profileSnapshotAssembler.assemble(userId, exam.getOrganizationId()));
      candidate.setProfileSnapshotTime(now);
      candidate.setCandidateStatus("NOT_STARTED");
      candidate.setAttemptCount(0);
      try {
        examCandidateMapper.insert(candidate);
      } catch (DuplicateKeyException ex) {
        ExamCandidate concurrent = examCandidateMapper.selectOne(new LambdaQueryWrapper<ExamCandidate>()
                                                                   .eq(ExamCandidate::getAssessmentExecutionId,
                                                                       assessmentExecutionId)
                                                                   .eq(ExamCandidate::getUserId, userId));
        if (concurrent == null) throw ex;
        candidate = concurrent;
      }
      result.add(toCandidateVO(candidate));
    }
    return result;
  }

  @Override
  public List<ExamReviewerVO> listReviewers(Long examId) {
    Exam exam = requireExam(examId);
    var execution = assessmentExecutionService.findStandaloneByExamId(examId);
    if (execution == null && "DRAFT".equals(exam.getPublishStatus())) return List.of();
    if (execution == null) execution = assessmentExecutionService.requireStandaloneByExamId(examId);
    return examReviewerQueryMapper.selectReviewers(execution.getAssessmentExecutionId());
  }

  @Override
  @Transactional
  public void replaceReviewers(Long examId, ExamReviewersReplaceRequest request) {
    Exam exam = requireExamForUpdate(examId);
    // 固定名单与条件范围互斥；先展开校验，再创建执行和替换原配置。
    if ((request.getSelection() == null) == (request.getReviewerUserIds() == null))
      throw new BusinessException(HttpStatus.BAD_REQUEST,7070,"请选择评卷人范围或固定名单，不能同时提交");
    List<Long> ids;
    String source;
    if (request.getSelection() != null) {
      ids=personSelectionService.resolve(request.getSelection());source=request.getSelection().getSelectionType();
      if(ids.isEmpty()) throw new BusinessException(HttpStatus.BAD_REQUEST,7070,"所选范围当前没有可用评卷人");
    } else {
      ids=request.getReviewerUserIds();source="USER";
      if(ids.stream().anyMatch(id -> id==null || id<=0)) throw new BusinessException(HttpStatus.BAD_REQUEST,7070,"请选择有效评卷人");
    }
    ids=ids.stream().distinct().toList();reviewerEligibility.requireEligible(exam.getOrganizationId(),ids);
    var execution = assessmentExecutionService.findStandaloneByExamId(examId);
    if (execution == null) {
      if ("DRAFT".equals(exam.getPublishStatus()) && ids.isEmpty()) return;
      execution = participantExecution(exam);
    }
    examReviewerMapper.delete(new LambdaQueryWrapper<ExamReviewer>()
                                .eq(ExamReviewer::getAssessmentExecutionId,
                                    execution.getAssessmentExecutionId()));
    for (Long userId : ids) {
      ExamReviewer reviewer = new ExamReviewer();
      reviewer.setAssessmentExecutionId(execution.getAssessmentExecutionId());
      reviewer.setReviewerUserId(userId);
      reviewer.setAssignmentSource(source);
      examReviewerMapper.insert(reviewer);
    }
  }

  private AssessmentExecution participantExecution(Exam exam) {
    var execution = assessmentExecutionService.findStandaloneByExamId(exam.getExamId());
    if (execution != null) return execution;
    if ("DRAFT".equals(exam.getPublishStatus())) {
      return assessmentExecutionService.ensureStandalone(exam);
    }
    return assessmentExecutionService.requireStandaloneByExamId(exam.getExamId());
  }

  @Override
  public PageResult<ExamCandidateVO> pageCandidates(Long examId, PageRequest<ExamCandidateQuery> request) {
    requireExam(examId);
    IPage<ExamCandidateVO> page = examCandidateQueryMapper.selectCandidatePage(
      new Page<>(request.getPageNum(), request.getPageSize()), examId, request.getQuery());
    return new PageResult<>(page.getRecords(), page.getTotal());
  }

  /**
   * 序列化题库练习抽题规则，并校验及格分不超过抽题总分。
   * <p>
   * 只有 {@code BANK_PRACTICE} 使用抽题规则；其余类型或未配置时返回 {@code null}。
   *
   * @param drawRules 请求中的抽题规则
   * @param examType  考试类型
   * @return 规则 JSON；无规则时返回 null
   */
  private String writeDrawRules(List<ExamDrawRuleRequest> drawRules, String examType) {
    if (!"BANK_PRACTICE".equals(examType) || drawRules == null || drawRules.isEmpty()) {
      return null;
    }
    String json;
    try {
      json = objectMapper.writeValueAsString(drawRules);
    } catch (JsonProcessingException e) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 7063, "抽题规则序列化失败");
    }
    List<QuestionBankDrawEngine.Rule> rules = QuestionBankDrawEngine.parseRules(json, objectMapper);
    return json;
  }

  /**
   * 独立题库练习的抽题规则来自考试自身配置；培训来源已在 extraPolicy 中带入，此处不覆盖。
   */
  private Map<String, Object> mergeStandaloneDrawRules(Exam exam, Map<String, Object> extraPolicy) {
    if (extraPolicy != null && extraPolicy.get("drawRules") != null) {
      return extraPolicy;
    }
    if (!"BANK_PRACTICE".equals(exam.getExamType())) {
      return extraPolicy;
    }
    if (!StringUtils.hasText(exam.getDrawRuleJson())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 7063, "题库练习未配置抽题规则");
    }
    List<Object> rules;
    try {
      rules = objectMapper.readValue(exam.getDrawRuleJson(), new TypeReference<List<Object>>() {
      });
    } catch (JsonProcessingException e) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 7063, "抽题规则无法解析");
    }
    Map<String, Object> merged = extraPolicy == null
      ? new LinkedHashMap<>() : new LinkedHashMap<>(extraPolicy);
    merged.put("drawRules", rules);
    return merged;
  }

  private void applyCreate(Exam exam, ExamCreateRequest request) {
    exam.setExamCode(request.getExamCode().trim());
    exam.setExamName(request.getExamName().trim());
    exam.setExamType(request.getExamType());
    exam.setExamCategoryId(request.getExamCategoryId());
    exam.setCoverFileId(request.getCoverFileId());
    exam.setPaperVersionId(request.getPaperVersionId());
    exam.setQuestionBankId(request.getQuestionBankId());
    exam.setResponsibleUserId(request.getResponsibleUserId());
    exam.setOpenStartTime(request.getOpenStartTime());
    exam.setOpenEndTime(request.getOpenEndTime());
    exam.setCredit(request.getCredit());
    exam.setIsNoticeEnabled(request.getIsNoticeEnabled() == null ? 0 : request.getIsNoticeEnabled());
    exam.setNoticeContent(request.getNoticeContent());
    exam.setStatus(request.getStatus());
    applyCreateRules(exam, request);
    exam.setDrawRuleJson(writeDrawRules(request.getDrawRules(), request.getExamType()));
  }

  /**
   * 创建时物化规则：无试卷必填；有试卷则省略从版本带出、提交则覆盖。
   */
  private void applyCreateRules(Exam exam, ExamCreateRequest request) {
    if (request.getPaperVersionId() == null) {
      boolean bankPractice = "BANK_PRACTICE".equals(request.getExamType());
      requireNoPaperRules(request.getDurationSeconds(), request.getPassRatio(), request.getPassScore(),
                          request.getQuestionOrderMode(), request.getMultiSelectScoring(),
                          !bankPractice);
      exam.setDurationSeconds(request.getDurationSeconds());
      // 题库练习不产生及格结论；表列非空，仅以 0 作为无及格规则的持久化占位。
      exam.setPassRatio(bankPractice ? BigDecimal.ZERO : request.getPassRatio());
      exam.setPassScore(bankPractice ? BigDecimal.ZERO : request.getPassScore());
      exam.setQuestionOrderMode(request.getQuestionOrderMode());
      exam.setMultiSelectScoring(request.getMultiSelectScoring());
      return;
    }
    PaperVersion version = requireBoundPaperVersion(request.getPaperVersionId());
    fillRulesFromPaper(exam, version, request.getDurationSeconds(), request.getPassRatio(), request.getPassScore(),
                       request.getQuestionOrderMode(), request.getMultiSelectScoring());
    if (request.getCoverFileId() == null && version.getPaperId() != null) {
      Paper paper = paperMapper.selectById(version.getPaperId());
      if (paper != null) {
        exam.setCoverFileId(paper.getCoverFileId());
      }
    }
  }

  /**
   * 草稿更新：换绑则重填省略字段；未换绑则保留原值。须在写入 paperVersionId 前调用。
   */
  private void applyUpdateRules(Exam exam, ExamUpdateRequest request) {
    boolean rebound = !Objects.equals(exam.getPaperVersionId(), request.getPaperVersionId());
    if (request.getPaperVersionId() == null) {
      if (rebound) {
        requireNoPaperRules(request.getDurationSeconds(), request.getPassRatio(), request.getPassScore(),
                            request.getQuestionOrderMode(), request.getMultiSelectScoring(),
                            !"BANK_PRACTICE".equals(exam.getExamType()));
        exam.setDurationSeconds(request.getDurationSeconds());
        exam.setPassRatio(request.getPassRatio());
        exam.setPassScore(request.getPassScore());
        exam.setQuestionOrderMode(request.getQuestionOrderMode());
        exam.setMultiSelectScoring(request.getMultiSelectScoring());
      } else {
        keepOrOverride(exam, request);
      }
      return;
    }
    if (rebound) {
      PaperVersion version = requireBoundPaperVersion(request.getPaperVersionId());
      fillRulesFromPaper(exam, version, request.getDurationSeconds(), request.getPassRatio(), request.getPassScore(),
                         request.getQuestionOrderMode(), request.getMultiSelectScoring());
      return;
    }
    keepOrOverride(exam, request);
    if (request.getPassRatio() != null && request.getPassScore() == null) {
      PaperVersion version = requireBoundPaperVersion(request.getPaperVersionId());
      exam.setPassScore(computePassScore(version.getTotalScore(), exam.getPassRatio()));
    }
  }

  /**
   * 未换绑时仅覆盖请求中非空的规则字段。
   */
  private void keepOrOverride(Exam exam, ExamUpdateRequest request) {
    if (request.getDurationSeconds() != null) {
      exam.setDurationSeconds(request.getDurationSeconds());
    }
    if (request.getPassRatio() != null) {
      exam.setPassRatio(request.getPassRatio());
    }
    if (request.getPassScore() != null) {
      exam.setPassScore(request.getPassScore());
    }
    if (StringUtils.hasText(request.getQuestionOrderMode())) {
      exam.setQuestionOrderMode(request.getQuestionOrderMode());
    }
    if (StringUtils.hasText(request.getMultiSelectScoring())) {
      exam.setMultiSelectScoring(request.getMultiSelectScoring());
    }
  }

  /**
   * 从试卷版本填充省略的规则字段；及格分允许本场覆盖，省略时按总分×生效比例计算。
   */
  private void fillRulesFromPaper(Exam exam, PaperVersion version, Integer durationSeconds,
                                  BigDecimal passRatio, BigDecimal passScore,
                                  String questionOrderMode, String multiSelectScoring) {
    exam.setDurationSeconds(durationSeconds != null ? durationSeconds : version.getDurationSeconds());
    exam.setPassRatio(passRatio != null ? passRatio : version.getPassRatio());
    exam.setQuestionOrderMode(
      StringUtils.hasText(questionOrderMode) ? questionOrderMode : version.getQuestionOrderMode());
    exam.setMultiSelectScoring(
      StringUtils.hasText(multiSelectScoring) ? multiSelectScoring : version.getMultiSelectScoring());
    exam.setPassScore(passScore != null ? passScore : computePassScore(version.getTotalScore(), exam.getPassRatio()));
  }

  private BigDecimal computePassScore(BigDecimal totalScore, BigDecimal passRatio) {
    BigDecimal total = totalScore == null ? BigDecimal.ZERO : totalScore;
    BigDecimal ratio = passRatio == null ? BigDecimal.ZERO : passRatio;
    return total.multiply(ratio).setScale(2, RoundingMode.HALF_UP);
  }

  private PaperVersion requireBoundPaperVersion(Long paperVersionId) {
    PaperVersion version = paperVersionMapper.selectById(paperVersionId);
    if (version == null) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 7005, "试卷版本不存在或不可用");
    }
    return version;
  }

  private void requireNoPaperRules(Integer durationSeconds, BigDecimal passRatio, BigDecimal passScore,
                                   String questionOrderMode, String multiSelectScoring,
                                   boolean requirePassingRules) {
    if (durationSeconds == null
      || !StringUtils.hasText(questionOrderMode) || !StringUtils.hasText(multiSelectScoring)
      || (requirePassingRules && (passRatio == null || passScore == null))) {
      throw new BusinessException(HttpStatus.BAD_REQUEST,
                                  1001,
                                  requirePassingRules
                                    ? "未绑定试卷时必须提供时长、及格比例、及格分、题序和多选计分"
                                    : "题库练习必须提供时长、题序和多选计分");
    }
  }

  private void upsertPolicy(Long examId, ExamPolicyRequest request) {
    ExamPolicyRequest src = request == null ? new ExamPolicyRequest() : request;
    ExamPolicy existing = examPolicyMapper.selectOne(new LambdaQueryWrapper<ExamPolicy>()
      .eq(ExamPolicy::getExamId, examId));
    if (existing == null) {
      ExamPolicy policy = new ExamPolicy();
      policy.setExamId(examId);
      BeanUtils.copyProperties(src, policy);
      ensurePolicyDefaults(policy);
      examPolicyMapper.insert(policy);
    } else {
      BeanUtils.copyProperties(src, existing, "examId");
      ensurePolicyDefaults(existing);
      examPolicyMapper.updateById(existing);
    }
  }

  private void ensurePolicyDefaults(ExamPolicy policy) {
    if (policy.getIsAnticheatEnabled() == null) policy.setIsAnticheatEnabled(0);
    if (policy.getIsWatermarkEnabled() == null) policy.setIsWatermarkEnabled(0);
    if (policy.getIsPcAllowed() == null) policy.setIsPcAllowed(1);
    if (policy.getIsAppAllowed() == null) policy.setIsAppAllowed(1);
    if (policy.getIsH5Allowed() == null) policy.setIsH5Allowed(1);
    if (policy.getIsSplitScreenForceSubmit() == null) policy.setIsSplitScreenForceSubmit(0);
    if (policy.getIsCopyRefreshDevtoolDisabled() == null) policy.setIsCopyRefreshDevtoolDisabled(0);
    if (policy.getIsRetakeEnabled() == null) policy.setIsRetakeEnabled(0);
    if (policy.getIsRetakeEnabled() == 1 && !StringUtils.hasText(policy.getRetakeScope())) {
      policy.setRetakeScope("FAILED");
    }
    if (policy.getRetakeMaxCount() == null) policy.setRetakeMaxCount(0);
    if (policy.getRetakeScoreMode() == null) policy.setRetakeScoreMode("HIGHEST");
    if (policy.getIsRetakeRecordVisible() == null) policy.setIsRetakeRecordVisible(1);
    if (policy.getPassPoint() == null) policy.setPassPoint(java.math.BigDecimal.ZERO);
    if (policy.getFailPoint() == null) policy.setFailPoint(java.math.BigDecimal.ZERO);
    if (policy.getAbsentPoint() == null) policy.setAbsentPoint(java.math.BigDecimal.ZERO);
    if (policy.getLateForbidSeconds() == null) policy.setLateForbidSeconds(0);
    if (policy.getAnswerDetailMode() == null) policy.setAnswerDetailMode("SUBMITTED");
    if (policy.getIsAnalysisVisible() == null) policy.setIsAnalysisVisible(1);
    if (policy.getIsScoreVisible() == null) policy.setIsScoreVisible(1);
    if (policy.getIsRankingEnabled() == null) policy.setIsRankingEnabled(0);
  }

  private Exam requireExam(Long id) {
    Exam exam = examMapper.selectById(id);
    if (exam == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 7001, "考试不存在");
    }
    return exam;
  }

  private Exam requireExamForUpdate(Long id) {
    Exam exam = examMapper.selectByIdForUpdate(id);
    if (exam == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 7001, "考试不存在");
    }
    return exam;
  }

  private ExamVO toVO(Exam exam) {
    return toVO(exam, false);
  }

  /**
   * @param withPolicy 是否装配考试策略。仅详情装配；列表逐行查 policy 会形成 N+1。
   */
  /**
   * 回读抽题规则。详情页面对脏数据做降级（返回空），避免整页打不开；
   * 规则真正生效路径（发布）仍会抛出 7063，不会把损坏的规则放行。
   */
  private List<ExamDrawRuleVO> readDrawRules(Exam exam) {
    if (!StringUtils.hasText(exam.getDrawRuleJson())) {
      return null;
    }
    try {
      return objectMapper.readValue(exam.getDrawRuleJson(), new TypeReference<List<ExamDrawRuleVO>>() {
      });
    } catch (JsonProcessingException e) {
      log.warn("考试 {} 的抽题规则无法解析，详情按未配置返回", exam.getExamId(), e);
      return null;
    }
  }

  private ExamVO toVO(Exam exam, boolean withPolicy) {
    ExamVO vo = new ExamVO();
    BeanUtils.copyProperties(exam, vo);
    vo.setDrawRules(readDrawRules(exam));
    fillResourceNames(vo);
    if (withPolicy) {
      ExamPolicy policy = examPolicyMapper.selectOne(new LambdaQueryWrapper<ExamPolicy>()
                                                       .eq(ExamPolicy::getExamId, exam.getExamId()));
      if (policy != null) {
        ExamPolicyVO policyVO = new ExamPolicyVO();
        BeanUtils.copyProperties(policy, policyVO);
        vo.setPolicy(policyVO);
      }
    }
    return vo;
  }

  /** 装配关联资源的可读名称，资源被历史删除时保留空值而不泄露内部 ID。 */
  private void fillResourceNames(ExamVO vo) {
    if (vo.getPaperVersionId() != null) {
      PaperVersion version = paperVersionMapper.selectById(vo.getPaperVersionId());
      if (version != null) {
        vo.setPaperVersionNo(version.getVersionNo());
        Paper paper = paperMapper.selectById(version.getPaperId());
        if (paper != null) {
          vo.setPaperName(paper.getPaperName());
        }
      }
    }
    if (vo.getQuestionBankId() != null) {
      QuestionBank questionBank = questionBankMapper.selectById(vo.getQuestionBankId());
      if (questionBank != null) {
        vo.setQuestionBankName(questionBank.getBankName());
      }
    }
  }

  private ExamCandidateVO toCandidateVO(ExamCandidate candidate) {
    ExamCandidateVO vo = new ExamCandidateVO();
    BeanUtils.copyProperties(candidate, vo);
    var execution = assessmentExecutionService.requireById(candidate.getAssessmentExecutionId());
    vo.setExamId(execution.getExamId());
    vo.setSourceType(execution.getSourceType());
    return vo;
  }
}
