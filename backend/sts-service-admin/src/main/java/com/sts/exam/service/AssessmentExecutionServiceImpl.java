package com.sts.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.common.exception.BusinessException;
import com.sts.course.entity.CourseAssessmentResult;
import com.sts.course.entity.CourseOffering;
import com.sts.course.mapper.CourseAssessmentResultMapper;
import com.sts.course.snapshot.CourseContentSnapshot;
import com.sts.course.snapshot.ItemSnapshot;
import com.sts.exam.entity.*;
import com.sts.exam.mapper.*;
import com.sts.paper.entity.Paper;
import com.sts.paper.entity.PaperVersion;
import com.sts.paper.mapper.PaperMapper;
import com.sts.paper.mapper.PaperVersionMapper;
import com.sts.question.entity.QuestionBank;
import com.sts.question.mapper.QuestionBankMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AssessmentExecutionServiceImpl implements AssessmentExecutionService {
  private final AssessmentExecutionMapper executionMapper;
  private final ExamPolicyMapper policyMapper;
  private final PaperVersionMapper paperVersionMapper;
  private final PaperMapper paperMapper;
  private final QuestionBankMapper questionBankMapper;
  private final ObjectMapper objectMapper;
  private final AssessmentExecutionSnapshotCodec snapshotCodec;
  private final ExamCandidateMapper candidateMapper;
  private final ExamReviewerMapper reviewerMapper;
  private final ExamAttemptMapper attemptMapper;
  private final AssessmentExecutionQuestionPoolMapper executionQuestionPoolMapper;
  private final CourseAssessmentResultMapper courseAssessmentResultMapper;
  private final ExamCandidateDeleteHelper candidateDeleteHelper;
  private final AssessmentExecutionDeleteHelper executionDeleteHelper;
  /** 仅在新课程执行创建时捕获关联，不刷新历史执行。 */
  private final CourseKnowledgeSnapshotService courseKnowledgeSnapshotService;

  @Override
  public AssessmentExecution ensureStandalone(Exam exam) {
    AssessmentExecution existing = findStandalone(exam.getExamId());
    if (existing != null) return existing;
    return insertOrLoad(buildFromExam(exam, null));
  }

  @Override
  @Transactional
  public AssessmentExecution refreshStandalone(Exam exam) {
    return refreshStandalone(exam, null);
  }

  @Override
  @Transactional
  public AssessmentExecution refreshStandalone(Exam exam, Map<String, Object> extraPolicy) {
    AssessmentExecution fresh = buildFromExam(exam, extraPolicy);
    AssessmentExecution existing = findStandalone(exam.getExamId());
    if (existing == null) {
      existing = insertOrLoad(fresh);
      if (existing == fresh) return fresh;
    }
    if (Objects.equals(existing.getSnapshotHash(), fresh.getSnapshotHash())) {
      return existing;
    }
    assertExecutionRefreshable(existing.getAssessmentExecutionId());
    // 草稿阶段的考生和阅卷人只是配置事实，发布时保留执行对象及其归属，仅覆盖最终快照。
    copySnapshot(existing, fresh);
    if (executionMapper.updateById(existing) != 1) {
      throw new BusinessException(HttpStatus.CONFLICT, 7004, "考试执行快照并发更新，请重试发布");
    }
    return existing;
  }

  /**
   * 按考试已有字段物化执行：有 planContentId 则为 TRAINING_*，否则 STANDALONE_*。
   */
  private AssessmentExecution buildFromExam(Exam exam, Map<String, Object> extraPolicy) {
    AssessmentExecution execution = base(exam.getOrganizationId());
    boolean practice = "BANK_PRACTICE".equals(exam.getExamType())
      || "SPECIAL_PRACTICE".equals(exam.getExamType());
    boolean training = exam.getPlanContentId() != null;
    boolean bankPractice = "BANK_PRACTICE".equals(exam.getExamType());
    if (training) {
      execution.setSourceType(bankPractice ? "TRAINING_PRACTICE" : "TRAINING_EXAM");
    } else {
      execution.setSourceType(practice ? "STANDALONE_PRACTICE" : "STANDALONE_EXAM");
    }
    execution.setSourceBusinessId(exam.getExamId());
    execution.setExamId(exam.getExamId());
    execution.setAssessmentKind(!practice ? "FORMAL_EXAM"
                                  : bankPractice ? "BANK_PRACTICE" : "SPECIAL_PRACTICE");
    if ("OFFLINE".equals(exam.getExamType())) {
      execution.setResourceType("OFFLINE_RESULT");
      execution.setResourceId(exam.getExamId());
      execution.setResourceNameSnapshot(exam.getExamName());
      execution.setDurationSecondsSnapshot(0);
      // 线下成绩默认按百分制录入；及格分允许高于 100 时，满分必须同步上移，确保及格判定可达。
      execution.setTotalScoreSnapshot(offlineTotalScore(exam.getPassScore()));
      execution.setPassScoreSnapshot(exam.getPassScore());
      execution.setQuestionOrderModeSnapshot("NOT_APPLICABLE");
      execution.setMultiSelectScoringSnapshot("NOT_APPLICABLE");
    } else if ("BANK_PRACTICE".equals(exam.getExamType())) {
      if (exam.getQuestionBankId() == null) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, 7063, "题库练习未绑定题库");
      }
      QuestionBank questionBank = questionBankMapper.selectById(exam.getQuestionBankId());
      if (questionBank == null) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, 7063, "题库不存在");
      }
      execution.setResourceType("QUESTION_BANK");
      execution.setResourceId(exam.getQuestionBankId());
      execution.setResourceNameSnapshot(questionBank.getBankName());
      // 题库练习与培训题库练习一致：只提供作答与自动反馈，不冻结正式分数。
      execution.setTotalScoreSnapshot(null);
    } else {
      fillPaper(execution, exam.getPaperVersionId());
    }
    execution.setDurationSecondsSnapshot(exam.getDurationSeconds());
    execution.setPassScoreSnapshot(bankPractice ? null : exam.getPassScore());
    execution.setQuestionOrderModeSnapshot(exam.getQuestionOrderMode());
    execution.setMultiSelectScoringSnapshot(exam.getMultiSelectScoring());
    // 及格分不得超过本场满分，否则抽题类考核在结构上不可能及格。
    if (execution.getPassScoreSnapshot() != null && execution.getTotalScoreSnapshot() != null
      && execution.getTotalScoreSnapshot().signum() > 0
      && execution.getPassScoreSnapshot().compareTo(execution.getTotalScoreSnapshot()) > 0) {
      execution.setPassScoreSnapshot(execution.getTotalScoreSnapshot());
    }
    ExamPolicy policy = policyMapper.selectOne(new LambdaQueryWrapper<ExamPolicy>()
                                                 .eq(ExamPolicy::getExamId, exam.getExamId()));
    freezeStandalone(execution, policy);
    applyExtraPolicy(execution, extraPolicy);
    return execution;
  }

  @Override
  public List<AssessmentExecution> ensureCourseExecutions(CourseOffering offering,
                                                          CourseContentSnapshot snapshot) {
    List<AssessmentExecution> result = new ArrayList<>();
    if (snapshot == null || snapshot.getChapters() == null) return result;
    for (var chapter : snapshot.getChapters()) {
      if (chapter.getItems() == null) continue;
      for (ItemSnapshot item : chapter.getItems()) {
        if (!"QUIZ".equals(item.getItemType()) && !"QUESTION_BANK".equals(item.getItemType())) continue;
        AssessmentExecution existing = findCourse(offering.getCourseOfferingId(),
                                                  offering.getCourseVersionId(), item.getCourseItemId());
        if (existing != null) {
          result.add(existing);
          continue;
        }
        AssessmentExecution execution = base(offering.getOrganizationId());
        execution.setSourceType("QUIZ".equals(item.getItemType()) ? "COURSE_QUIZ" : "COURSE_BANK_PRACTICE");
        execution.setSourceBusinessId(item.getCourseItemId());
        execution.setCourseOfferingId(offering.getCourseOfferingId());
        execution.setCourseVersionId(offering.getCourseVersionId());
        execution.setCourseItemId(item.getCourseItemId());
        execution.setAssessmentKind("QUIZ".equals(item.getItemType()) ? "QUIZ" : "BANK_PRACTICE");
        if ("QUIZ".equals(item.getItemType())) {
          fillPaper(execution, item.getPaperVersionId());
          if (Integer.valueOf(1).equals(item.getIsFinalAssessment())) execution.setPassScoreSnapshot(item.getPassScore());
        } else {
          execution.setResourceType("QUESTION_BANK");
          execution.setResourceId(item.getQuestionBankId());
          execution.setResourceNameSnapshot(item.getQuestionBankName());
          execution.setDurationSecondsSnapshot(0);
          execution.setTotalScoreSnapshot(BigDecimal.ZERO);
          execution.setPassScoreSnapshot(BigDecimal.ZERO);
          execution.setQuestionOrderModeSnapshot("IMPORT");
          execution.setMultiSelectScoringSnapshot("ALL_OR_NOTHING");
        }
        freezePolicy(execution, Map.of("completionRule", "ANSWER_ALL", "accuracyThreshold", 0,
          "isFinalAssessment", Integer.valueOf(1).equals(item.getIsFinalAssessment()) ? 1 : 0,
          "knowledgePointSnapshots", courseKnowledgeSnapshotService.freeze(item.getPaperVersionId(),item.getQuestionBankId())));
        result.add(insertOrLoad(execution));
      }
    }
    return result;
  }

  @Override
  public AssessmentExecution requireById(Long id) {
    AssessmentExecution execution = executionMapper.selectById(id);
    if (execution == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 7061, "考核执行不存在");
    }
    return verify(execution);
  }

  @Override
  public AssessmentExecution requireStandaloneByExamId(Long examId) {
    AssessmentExecution execution = findStandalone(examId);
    if (execution == null) throw new BusinessException(HttpStatus.CONFLICT, 7062, "考试尚未生成执行对象");
    return execution;
  }

  @Override
  public AssessmentExecution findStandaloneByExamId(Long examId) {
    return findStandalone(examId);
  }

  @Override
  public void deleteStandaloneIfUnused(Long examId) {
    AssessmentExecution execution = findStandalone(examId);
    if (execution == null) return;
    Long attemptCount = attemptMapper.selectCount(new LambdaQueryWrapper<ExamAttempt>()
                                                    .eq(ExamAttempt::getAssessmentExecutionId,
                                                        execution.getAssessmentExecutionId()));
    if (attemptCount != null && attemptCount > 0) {
      throw new BusinessException(HttpStatus.CONFLICT, 7003, "草稿考试已存在作答，无法删除");
    }
    reviewerMapper.delete(new LambdaQueryWrapper<ExamReviewer>()
                            .eq(ExamReviewer::getAssessmentExecutionId,
                                execution.getAssessmentExecutionId()));
    List<ExamCandidate> candidates = candidateMapper.selectList(
      new LambdaQueryWrapper<ExamCandidate>()
        .eq(ExamCandidate::getAssessmentExecutionId,
            execution.getAssessmentExecutionId()));
    for (var candidate : candidates) {
      candidateDeleteHelper.removeById(candidate.getExamCandidateId());
    }
    executionDeleteHelper.removeById(execution.getAssessmentExecutionId());
  }

  @Override
  public AssessmentExecution requireCourse(Long offeringId, Long versionId, Long itemId) {
    AssessmentExecution execution = findCourse(offeringId, versionId, itemId);
    if (execution == null) throw new BusinessException(HttpStatus.CONFLICT, 7062, "课程考核尚未生成执行对象");
    return execution;
  }

  private void applyExtraPolicy(AssessmentExecution execution, Map<String, Object> extraPolicy) {
    if (extraPolicy == null || extraPolicy.isEmpty()) {
      return;
    }
    Map<String, Object> overlay = new LinkedHashMap<>(extraPolicy);
    Object kind = overlay.remove("assessmentKind");
    if (kind != null && !kind.toString().isBlank()) {
      execution.setAssessmentKind(kind.toString());
    }
    if (!overlay.isEmpty()) {
      mergeExtraPolicy(execution, overlay);
    }
  }

  private void mergeExtraPolicy(AssessmentExecution execution, Map<String, Object> extraPolicy) {
    try {
      Map<String, Object> policy = objectMapper.readValue(
        execution.getPolicySnapshotJson(), new TypeReference<LinkedHashMap<String, Object>>() {
        });
      policy.putAll(extraPolicy);
      execution.setPolicySnapshotJson(objectMapper.writeValueAsString(policy));
      codec().encode(execution);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("培训考核策略快照合并失败", e);
    }
  }

  private AssessmentExecution base(Long organizationId) {
    AssessmentExecution execution = new AssessmentExecution();
    execution.setOrganizationId(organizationId);
    execution.setExecutionStatus("READY");
    return execution;
  }

  private void fillPaper(AssessmentExecution execution, Long paperVersionId) {
    if (paperVersionId == null) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 7005, "考试未绑定试卷版本");
    }
    PaperVersion version = paperVersionMapper.selectById(paperVersionId);
    Paper paper = version == null ? null : paperMapper.selectById(version.getPaperId());
    if (version == null || paper == null || !"PUBLISHED".equals(version.getPublishedStatus())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 7005, "试卷版本未发布");
    }
    execution.setResourceType("PAPER_VERSION");
    execution.setResourceId(version.getPaperVersionId());
    execution.setResourceNameSnapshot(paper.getPaperName());
    execution.setDurationSecondsSnapshot(version.getDurationSeconds());
    execution.setTotalScoreSnapshot(version.getTotalScore());
    execution.setPassScoreSnapshot(version.getPassScore());
    execution.setQuestionOrderModeSnapshot(version.getQuestionOrderMode());
    execution.setMultiSelectScoringSnapshot(version.getMultiSelectScoring());
  }

  private void freezePolicy(AssessmentExecution execution, Object policy) {
    try {
      String json = objectMapper.writeValueAsString(policy);
      execution.setPolicySnapshotJson(json);
      codec().encode(execution);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("考核规则快照序列化失败", e);
    }
  }

  private void freezeStandalone(AssessmentExecution execution, ExamPolicy policy) {
    Map<String, Object> policySnapshot = standalonePolicySnapshot(policy);
    try {
      execution.setPolicySnapshotJson(objectMapper.writeValueAsString(policySnapshot));
      codec().encode(execution);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("考核规则快照序列化失败", e);
    }
  }

  private Map<String, Object> standalonePolicySnapshot(ExamPolicy policy) {
    if (policy == null) return Map.of();
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("isAnticheatEnabled", policy.getIsAnticheatEnabled());
    snapshot.put("isWatermarkEnabled", policy.getIsWatermarkEnabled());
    snapshot.put("isPcAllowed", policy.getIsPcAllowed());
    snapshot.put("isAppAllowed", policy.getIsAppAllowed());
    snapshot.put("isH5Allowed", policy.getIsH5Allowed());
    snapshot.put("switchLimit", policy.getSwitchLimit());
    snapshot.put("isSplitScreenForceSubmit", policy.getIsSplitScreenForceSubmit());
    snapshot.put("isCopyRefreshDevtoolDisabled", policy.getIsCopyRefreshDevtoolDisabled());
    snapshot.put("inactivitySeconds", policy.getInactivitySeconds());
    snapshot.put("inactivityCountdownSeconds", policy.getInactivityCountdownSeconds());
    snapshot.put("minimumAnswerSeconds", policy.getMinimumAnswerSeconds());
    snapshot.put("isRetakeEnabled", policy.getIsRetakeEnabled());
    snapshot.put("retakeScope", policy.getRetakeScope());
    snapshot.put("retakeStartTime",
                 policy.getRetakeStartTime() == null ? null : policy.getRetakeStartTime().toString());
    snapshot.put("retakeEndTime", policy.getRetakeEndTime() == null ? null : policy.getRetakeEndTime().toString());
    snapshot.put("retakeMaxCount", policy.getRetakeMaxCount());
    snapshot.put("retakeScoreMode", policy.getRetakeScoreMode());
    snapshot.put("isRetakeRecordVisible", policy.getIsRetakeRecordVisible());
    snapshot.put("passPoint", normalizeDecimal(policy.getPassPoint()));
    snapshot.put("failPoint", normalizeDecimal(policy.getFailPoint()));
    snapshot.put("absentPoint", normalizeDecimal(policy.getAbsentPoint()));
    snapshot.put("lateForbidSeconds", policy.getLateForbidSeconds());
    snapshot.put("answerDetailMode", policy.getAnswerDetailMode());
    snapshot.put("isAnalysisVisible", policy.getIsAnalysisVisible());
    snapshot.put("isScoreVisible", policy.getIsScoreVisible());
    snapshot.put("isRankingEnabled", policy.getIsRankingEnabled());
    return snapshot;
  }

  private String normalizeDecimal(BigDecimal value) {
    return value == null ? null : value.stripTrailingZeros().toPlainString();
  }

  private AssessmentExecution insertOrLoad(AssessmentExecution execution) {
    codec().encode(execution);
    try {
      executionMapper.insert(execution);
      return execution;
    } catch (DuplicateKeyException e) {
      return execution.getExamId() != null ? requireStandaloneByExamId(execution.getExamId())
        : requireCourse(execution.getCourseOfferingId(), execution.getCourseVersionId(), execution.getCourseItemId());
    }
  }

  private AssessmentExecution findStandalone(Long examId) {
    return verifyNullable(executionMapper.selectOne(new LambdaQueryWrapper<AssessmentExecution>()
                                                      .eq(AssessmentExecution::getExamId, examId)));
  }

  @Override
  public AssessmentExecution findCourse(Long offeringId, Long versionId, Long itemId) {
    return verifyNullable(executionMapper.selectOne(new LambdaQueryWrapper<AssessmentExecution>()
                                                      .eq(AssessmentExecution::getCourseOfferingId, offeringId)
                                                      .eq(AssessmentExecution::getCourseVersionId, versionId)
                                                      .eq(AssessmentExecution::getCourseItemId, itemId)));
  }

  /**
   * 已产生作答、冻结题池或考核结果事实的执行快照不可原地刷新；人员配置仍可在发布前调整。
   */
  private void assertExecutionRefreshable(Long executionId) {
    boolean used = attemptMapper.selectCount(new LambdaQueryWrapper<ExamAttempt>()
                                     .eq(ExamAttempt::getAssessmentExecutionId, executionId)) > 0
      || executionQuestionPoolMapper.selectCount(
      new LambdaQueryWrapper<AssessmentExecutionQuestionPool>()
        .eq(AssessmentExecutionQuestionPool::getAssessmentExecutionId, executionId)) > 0
      || courseAssessmentResultMapper.selectCount(new LambdaQueryWrapper<CourseAssessmentResult>()
                                                    .eq(CourseAssessmentResult::getAssessmentExecutionId,
                                                        executionId)) > 0;
    if (used) {
      throw new BusinessException(HttpStatus.CONFLICT, 7003, "考核执行已产生业务事实，无法刷新快照");
    }
  }

  /** 计算线下考试可录入的满分，兼容默认百分制与高于 100 分的及格线配置。 */
  private BigDecimal offlineTotalScore(BigDecimal passScore) {
    BigDecimal defaultTotal = BigDecimal.valueOf(100);
    return passScore == null || passScore.compareTo(defaultTotal) < 0 ? defaultTotal : passScore;
  }

  /** 将新配置的快照字段写回草稿执行对象，保持考生和阅卷人关联不变。 */
  private void copySnapshot(AssessmentExecution target, AssessmentExecution source) {
    target.setOrganizationId(source.getOrganizationId());
    target.setSourceType(source.getSourceType());
    target.setSourceBusinessId(source.getSourceBusinessId());
    target.setExamId(source.getExamId());
    target.setCourseOfferingId(source.getCourseOfferingId());
    target.setCourseVersionId(source.getCourseVersionId());
    target.setCourseItemId(source.getCourseItemId());
    target.setAssessmentKind(source.getAssessmentKind());
    target.setResourceType(source.getResourceType());
    target.setResourceId(source.getResourceId());
    target.setExecutionStatus(source.getExecutionStatus());
    target.setResourceNameSnapshot(source.getResourceNameSnapshot());
    target.setDurationSecondsSnapshot(source.getDurationSecondsSnapshot());
    target.setTotalScoreSnapshot(source.getTotalScoreSnapshot());
    target.setPassScoreSnapshot(source.getPassScoreSnapshot());
    target.setQuestionOrderModeSnapshot(source.getQuestionOrderModeSnapshot());
    target.setMultiSelectScoringSnapshot(source.getMultiSelectScoringSnapshot());
    target.setPolicySnapshotJson(source.getPolicySnapshotJson());
    target.setSnapshotSchemaVersion(source.getSnapshotSchemaVersion());
    target.setSnapshotJson(source.getSnapshotJson());
    target.setSnapshotHash(source.getSnapshotHash());
  }

  /**
   * 校验持久化快照并返回已填充的只读视图。
   */
  private AssessmentExecution verify(AssessmentExecution execution) {
    codec().decodeAndVerify(execution);
    return execution;
  }

  private AssessmentExecution verifyNullable(AssessmentExecution execution) {
    return execution == null ? null : verify(execution);
  }

  private AssessmentExecutionSnapshotCodec codec() {
    return snapshotCodec;
  }
}
