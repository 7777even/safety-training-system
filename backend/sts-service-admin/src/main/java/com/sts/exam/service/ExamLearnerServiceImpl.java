package com.sts.exam.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.admin.entity.SysFileInfo;
import com.sts.admin.mapper.SysFileInfoMapper;
import com.sts.admin.storage.StorageService;
import com.sts.admin.tenant.TenantContext;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.common.exception.BusinessException;
import com.sts.exam.dto.ExamLearnerTaskQuery;
import com.sts.exam.entity.*;
import com.sts.exam.mapper.*;
import com.sts.exam.vo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 学员视图只基于当前登录人关联的 Candidate 读取数据；不得由请求参数指定用户。
 */
@Service
@RequiredArgsConstructor
public class ExamLearnerServiceImpl implements ExamLearnerService {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private final ExamCandidateMapper candidateMapper;
  private final ExamMapper examMapper;
  private final ExamAttemptMapper attemptMapper;
  private final ExamPolicyMapper policyMapper;
  private final ExamAttemptQuestionMapper questionMapper;
  private final ExamAttemptOptionMapper optionMapper;
  private final ExamAnswerMapper answerMapper;
  private final ExamAnswerItemMapper answerItemMapper;
  private final AssessmentExecutionService assessmentExecutionService;
  private final SysFileInfoMapper fileInfoMapper;
  private final StorageService storageService;

  @Override
  public PageResult<ExamLearnerTaskVO> pageMyTasks(PageRequest<ExamLearnerTaskQuery> request) {
    Long userId = StpUtil.getLoginIdAsLong();
    ExamLearnerTaskQuery query = request.getQuery();
    List<ExamCandidate> candidates = candidateMapper.selectList(new LambdaQueryWrapper<ExamCandidate>()
                                                                  .eq(ExamCandidate::getUserId, userId)
                                                                  .orderByDesc(ExamCandidate::getUpdateTime)
                                                                  .orderByDesc(ExamCandidate::getExamCandidateId));
    if (candidates.isEmpty()) return new PageResult<>(List.of(), 0);

    Map<Long, AssessmentExecution> executions = candidates.stream()
      .map(ExamCandidate::getAssessmentExecutionId).distinct()
      .map(assessmentExecutionService::requireById)
      .filter(execution -> execution.getExamId() != null)
      // 「我的考试」入口只含独立考试来源；培训考试从培训入口进入。
      .filter(execution -> !"TRAINING_EXAM".equals(execution.getSourceType())
        && !"TRAINING_PRACTICE".equals(execution.getSourceType()))
      .collect(Collectors.toMap(AssessmentExecution::getAssessmentExecutionId, Function.identity()));
    // 全部候选均为培训来源时，避免空集合被 MyBatis-Plus 展开成 IN ()。
    if (executions.isEmpty()) return new PageResult<>(List.of(), 0);
    Map<Long, Exam> exams = examMapper.selectBatchIds(executions.values().stream()
                                                        .map(AssessmentExecution::getExamId).distinct().toList()).stream()
      .filter(exam -> List.of("PUBLISHED", "RUNNING", "ENDED").contains(exam.getPublishStatus()))
      .collect(Collectors.toMap(Exam::getExamId, Function.identity()));
    if (exams.isEmpty()) return new PageResult<>(List.of(), 0);
    Map<Long, ExamAttempt> attempts = latestAttempts(candidates, userId);
    Map<Long, ExamPolicy> policies = policyMapper.selectList(new LambdaQueryWrapper<ExamPolicy>()
                                                               .in(ExamPolicy::getExamId, exams.keySet()))
      .stream().collect(Collectors.toMap(ExamPolicy::getExamId, Function.identity()));

    List<ExamLearnerTaskVO> all = candidates.stream()
      .map(candidate -> executions.get(candidate.getAssessmentExecutionId()))
      .filter(Objects::nonNull)
      .map(execution -> {
        ExamCandidate candidate = candidates.stream()
          .filter(value -> Objects.equals(value.getAssessmentExecutionId(), execution.getAssessmentExecutionId()))
          .findFirst().orElse(null);
        return toTaskVO(candidate, execution, exams.get(execution.getExamId()),
                        attempts.get(candidate.getExamCandidateId()), policies.get(execution.getExamId()));
      })
      .filter(Objects::nonNull)
      .filter(task -> query == null || !StringUtils.hasText(query.getExamName())
        || task.getExamName().contains(query.getExamName()))
      .filter(task -> query == null || !StringUtils.hasText(query.getTaskStatus())
        || query.getTaskStatus().equals(task.getTaskStatus()))
      .filter(task -> query == null || !StringUtils.hasText(query.getSourceType())
        || query.getSourceType().equals(task.getSourceType()))
      .toList();
    int from = Math.min((request.getPageNum() - 1) * request.getPageSize(), all.size());
    int to = Math.min(from + request.getPageSize(), all.size());
    return new PageResult<>(all.subList(from, to), all.size());
  }

  @Override
  public ExamAttemptVO getMyAttempt(Long examAttemptId) {
    Long userId = StpUtil.getLoginIdAsLong();
    ExamAttempt attempt = attemptMapper.selectById(examAttemptId);
    if (attempt == null) throw new BusinessException(HttpStatus.NOT_FOUND, 7020, "作答尝试不存在");
    ExamCandidate candidate = candidateMapper.selectById(attempt.getExamCandidateId());
    if (candidate == null || !Objects.equals(userId, attempt.getUserId())
      || !Objects.equals(userId, candidate.getUserId())) {
      throw new BusinessException(HttpStatus.FORBIDDEN, 7042, "无权访问该作答尝试");
    }
    AssessmentExecution execution = assessmentExecutionService.requireById(attempt.getAssessmentExecutionId());
    Exam exam = execution.getExamId() == null ? null : examMapper.selectById(execution.getExamId());
    if (execution.getExamId() != null && exam == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 7001, "独立考试不存在");
    }
    ExamPolicy policy = exam == null ? null : policyMapper.selectOne(new LambdaQueryWrapper<ExamPolicy>()
                                                                       .eq(ExamPolicy::getExamId, exam.getExamId()));
    return toAttemptVO(attempt, candidate, execution, exam, policy);
  }

  @Override
  public ExamLearnerNoticeVO getMyNotice(Long examId) {
    Long org = TenantContext.requireTenantId(), user = StpUtil.getLoginIdAsLong();
    Exam exam = examMapper.selectById(examId);
    if (exam == null || !Objects.equals(org, exam.getOrganizationId()) || !Integer.valueOf(1).equals(exam.getStatus())
      || !List.of("PUBLISHED", "RUNNING", "ENDED").contains(exam.getPublishStatus())) throw noticeDenied();
    AssessmentExecution execution = assessmentExecutionService.findStandaloneByExamId(examId);
    if (execution == null || !Objects.equals(org, execution.getOrganizationId()) || !Objects.equals(examId, execution.getExamId())
      || !List.of("STANDALONE_EXAM", "STANDALONE_PRACTICE").contains(execution.getSourceType())
      || "VOID".equals(execution.getExecutionStatus())) throw noticeDenied();
    ExamCandidate candidate = candidateMapper.selectOne(new LambdaQueryWrapper<ExamCandidate>()
      .eq(ExamCandidate::getAssessmentExecutionId, execution.getAssessmentExecutionId()).eq(ExamCandidate::getUserId, user));
    if (candidate == null || !Objects.equals(user, candidate.getUserId()) || !Objects.equals(org, candidate.getOrganizationId())) throw noticeDenied();
    return new ExamLearnerNoticeVO(examId, exam.getExamName(), exam.getExamType(), exam.getOpenStartTime(), exam.getOpenEndTime(),
      exam.getDurationSeconds(), exam.getPassRatio(), exam.getPassScore(), exam.getIsNoticeEnabled(), exam.getNoticeContent());
  }

  /** 不区分不存在与非本人资源，避免通过须知猜测考生关系。 */
  private BusinessException noticeDenied() { return new BusinessException(HttpStatus.NOT_FOUND, 7001, "考试不存在或当前账号无权访问"); }

  @Override
  public ExamCoverFile openCover(Long examId) {
    Long userId = StpUtil.getLoginIdAsLong();
    Exam exam = examMapper.selectById(examId);
    if (exam == null || !List.of("PUBLISHED", "RUNNING", "ENDED").contains(exam.getPublishStatus())) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 7001, "考试不存在");
    }
    AssessmentExecution execution = assessmentExecutionService.findStandaloneByExamId(examId);
    if (execution == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 7001, "考试不存在");
    }
    ExamCandidate candidate = candidateMapper.selectOne(new LambdaQueryWrapper<ExamCandidate>()
                                                          .eq(ExamCandidate::getAssessmentExecutionId,
                                                              execution.getAssessmentExecutionId())
                                                          .eq(ExamCandidate::getUserId, userId));
    if (candidate == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 7001, "考试不存在");
    }
    if (exam.getCoverFileId() == null) {
      throw coverNotFound();
    }
    SysFileInfo file = fileInfoMapper.selectById(exam.getCoverFileId());
    if (file == null || !StringUtils.hasText(file.getObjectName())) {
      throw coverNotFound();
    }
    Resource resource;
    try {
      resource = storageService.download(file.getObjectName());
      if (resource == null || !resource.exists()) {
        throw coverNotFound();
      }
    } catch (BusinessException error) {
      throw error;
    } catch (RuntimeException error) {
      throw coverNotFound();
    }
    long fileSize = file.getFileSize() == null ? 0L : file.getFileSize();
    int version = file.getVersion() == null ? 0 : file.getVersion();
    return new ExamCoverFile(resource, file.getFileName(), contentType(file.getFileSuffix()), fileSize,
                             "\"" + file.getFileInfoId() + "-" + version + "-" + fileSize + "\"");
  }

  private static BusinessException coverNotFound() {
    return new BusinessException(HttpStatus.NOT_FOUND, 7071, "考试封面不存在");
  }

  private static String contentType(String suffix) {
    String key = suffix == null ? "" : suffix.toLowerCase(java.util.Locale.ROOT);
    if ("jpg".equals(key) || "jpeg".equals(key)) return "image/jpeg";
    if ("png".equals(key)) return "image/png";
    if ("webp".equals(key)) return "image/webp";
    return "application/octet-stream";
  }

  private Map<Long, ExamAttempt> latestAttempts(List<ExamCandidate> candidates, Long userId) {
    List<ExamAttempt> attempts = attemptMapper.selectList(new LambdaQueryWrapper<ExamAttempt>()
                                                            .eq(ExamAttempt::getUserId, userId)
                                                            .in(ExamAttempt::getExamCandidateId,
                                                                candidates.stream().map(ExamCandidate::getExamCandidateId).toList())
                                                            .orderByDesc(ExamAttempt::getAttemptNo)
                                                            .orderByDesc(ExamAttempt::getExamAttemptId));
    Map<Long, ExamAttempt> result = new HashMap<>();
    for (ExamAttempt attempt : attempts) result.putIfAbsent(attempt.getExamCandidateId(), attempt);
    return result;
  }

  private ExamLearnerTaskVO toTaskVO(ExamCandidate candidate, AssessmentExecution execution,
                                     Exam exam, ExamAttempt attempt,
                                     ExamPolicy policy) {
    if (exam == null) return null;
    ExamLearnerTaskVO vo = new ExamLearnerTaskVO();
    vo.setExamCandidateId(candidate.getExamCandidateId());
    vo.setCandidateStatus(candidate.getCandidateStatus());
    vo.setAttemptCount(candidate.getAttemptCount());
    vo.setExamId(exam.getExamId());
    vo.setExamName(exam.getExamName());
    vo.setCoverUrl(exam.getCoverFileId() == null
                     ? null : "/api/exam/learner/exams/" + exam.getExamId() + "/cover");
    vo.setExamType(exam.getExamType());
    vo.setSourceType(execution.getSourceType());
    vo.setSourceBusinessId(execution.getSourceBusinessId());
    vo.setOpenStartTime(exam.getOpenStartTime());
    vo.setOpenEndTime(exam.getOpenEndTime());
    vo.setDurationSeconds(exam.getDurationSeconds());
    if (attempt != null) {
      vo.setCurrentAttemptId(attempt.getExamAttemptId());
      vo.setAttemptStatus(attempt.getAttemptStatus());
      vo.setDeadlineTime(attempt.getDeadlineTime());
    }
    applyResultVisibility(vo, attempt, candidate, exam, policy);
    // 线下考试无在线作答环节，成绩由管理端录入，任务状态与入口动作单独推导。
    boolean offlineResult = "OFFLINE_RESULT".equals(execution.getResourceType());
    vo.setTaskStatus(taskStatus(attempt, offlineResult));
    vo.setAvailableAction(actionFor(attempt, vo.getScorePublishStatus(), offlineResult));
    return vo;
  }

  private ExamAttemptVO toAttemptVO(ExamAttempt attempt, ExamCandidate candidate,
                                    AssessmentExecution execution, Exam exam,
                                    ExamPolicy policy) {
    ExamAttemptVO vo = new ExamAttemptVO();
    BeanUtils.copyProperties(attempt, vo);
    vo.setExamId(execution.getExamId());
    vo.setResourceType(execution.getResourceType());
    vo.setResourceId(execution.getResourceId());
    vo.setResourceName(execution.getResourceNameSnapshot());
    // 课程考核按自动生效裁剪；独立考试仍按人工发布和 ExamPolicy。
    boolean courseSource = isCourseSource(execution);
    boolean unscoredTrainingPractice = "TRAINING_PRACTICE".equals(execution.getSourceType())
      && attempt.getTotalScoreSnapshot() == null;
    boolean standaloneBankPractice = isStandaloneBankPractice(attempt);
    boolean published = isPublished(attempt);
    boolean effective = isCourseEffective(attempt);
    boolean scoreVisible = !unscoredTrainingPractice
      && (standaloneBankPractice ? effective : courseSource ? effective : published && isScoreVisible(policy));
    vo.setScorePublishStatus(standaloneBankPractice
                               ? (effective ? "EFFECTIVE" : "UNPUBLISHED")
                               : courseSource || unscoredTrainingPractice
                               ? (effective ? "EFFECTIVE" : "UNPUBLISHED")
                               : (published ? "PUBLISHED" : "UNPUBLISHED"));
    vo.setScoreVisible(scoreVisible);
    // 考试防作弊与水印是考试域策略：独立考试与培训专属考试/练习统一消费发布时冻结的开关，
    // 后续编辑策略不得改变进行中的作答规则；课程考核由课程自身的学习防作弊开关控制。
    boolean examPolicySource = !courseSource;
    vo.setIsAnticheatEnabled(examPolicySource
      ? snapshotPolicySwitch(execution, "isAnticheatEnabled",
                             policy == null || policy.getIsAnticheatEnabled() == null ? 0 : policy.getIsAnticheatEnabled()) : 0);
    vo.setIsWatermarkEnabled(examPolicySource
      ? snapshotPolicySwitch(execution, "isWatermarkEnabled",
                             policy == null || policy.getIsWatermarkEnabled() == null ? 0 : policy.getIsWatermarkEnabled()) : 0);
    if (!scoreVisible) {
      hideAttemptScores(vo);
      if (unscoredTrainingPractice && effective) {
        vo.setCorrectCount(attempt.getCorrectCount());
        vo.setWrongCount(attempt.getWrongCount());
        vo.setEvaluatedCount(attempt.getEvaluatedCount());
        vo.setUnassessedCount(attempt.getUnassessedCount());
        vo.setAccuracyRate(attempt.getAccuracyRate());
      }
    }

    List<ExamAttemptQuestion> questions = questionMapper.selectList(new LambdaQueryWrapper<ExamAttemptQuestion>()
                                                                      .eq(ExamAttemptQuestion::getExamAttemptId,
                                                                          attempt.getExamAttemptId())
                                                                      .orderByAsc(ExamAttemptQuestion::getSortOrder));
    Map<Long, List<ExamAttemptOption>> options = groupOptions(questions);
    Map<Long, ExamAnswerVO> answers = answersByQuestion(attempt.getExamAttemptId());
    boolean detailsVisible = standaloneBankPractice
      ? effective
      : unscoredTrainingPractice
      ? effective
      : courseSource
      ? scoreVisible
      : exam != null && answerDetailsVisible(attempt, candidate, exam, policy, published);
    if (!detailsVisible) hideAttemptDetailScores(vo);
    vo.setQuestions(questions.stream().map(question -> {
      ExamAttemptQuestionVO questionVO = new ExamAttemptQuestionVO();
      BeanUtils.copyProperties(question, questionVO);
      questionVO.setBlankCount(resolveBlankCount(question));
      if (detailsVisible) {
        questionVO.setStandardAnswer(standardAnswer(question.getCorrectAnswerSnapshotJson()));
        if (!courseSource && !standaloneBankPractice
            && (policy == null || !Objects.equals(policy.getIsAnalysisVisible(), 1))) {
          questionVO.setAnalysisSnapshot(null);
        }
      } else {
        questionVO.setAnalysisSnapshot(null);
        questionVO.setCorrectAnswerSnapshotJson(null);
      }
      questionVO.setOptions(options.getOrDefault(question.getAttemptQuestionId(), List.of()).stream().map(option -> {
        ExamAttemptOptionVO optionVO = new ExamAttemptOptionVO();
        BeanUtils.copyProperties(option, optionVO);
        if (!detailsVisible) optionVO.setIsCorrectSnapshot(null);
        return optionVO;
      }).toList());
      ExamAnswerVO answer = answers.get(question.getAttemptQuestionId());
      if (answer != null && !detailsVisible) hideAnswerScores(answer);
      questionVO.setAnswer(answer);
      return questionVO;
    }).toList());
    return vo;
  }

  private String standardAnswer(String correctAnswerSnapshotJson) {
    if (!StringUtils.hasText(correctAnswerSnapshotJson)) return null;
    try {
      JsonNode value = OBJECT_MAPPER.readTree(correctAnswerSnapshotJson).get("standardAnswer");
      return value == null || value.isNull() ? null : value.asText();
    } catch (Exception ignored) {
      return null;
    }
  }

  private Map<Long, List<ExamAttemptOption>> groupOptions(List<ExamAttemptQuestion> questions) {
    if (questions.isEmpty()) return Map.of();
    return optionMapper.selectList(new LambdaQueryWrapper<ExamAttemptOption>()
                                     .in(ExamAttemptOption::getAttemptQuestionId,
                                         questions.stream().map(ExamAttemptQuestion::getAttemptQuestionId).toList())
                                     .orderByAsc(ExamAttemptOption::getSortOrder))
      .stream().collect(Collectors.groupingBy(ExamAttemptOption::getAttemptQuestionId));
  }

  private Map<Long, ExamAnswerVO> answersByQuestion(Long attemptId) {
    List<ExamAnswer> answers = answerMapper.selectList(new LambdaQueryWrapper<ExamAnswer>()
                                                         .eq(ExamAnswer::getExamAttemptId, attemptId));
    if (answers.isEmpty()) return Map.of();
    Map<Long, List<ExamAnswerItemVO>> items = answerItemMapper.selectList(new LambdaQueryWrapper<ExamAnswerItem>()
                                                                            .in(ExamAnswerItem::getExamAnswerId,
                                                                                answers.stream().map(ExamAnswer::getExamAnswerId).toList())
                                                                            .orderByAsc(ExamAnswerItem::getSortOrder))
      .stream().map(item -> {
        ExamAnswerItemVO vo = new ExamAnswerItemVO();
        BeanUtils.copyProperties(item, vo);
        return Map.entry(item.getExamAnswerId(), vo);
      }).collect(Collectors.groupingBy(Map.Entry::getKey,
                                       Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
    return answers.stream().map(answer -> {
      ExamAnswerVO vo = new ExamAnswerVO();
      BeanUtils.copyProperties(answer, vo);
      // 批阅意见属于内部评分事实，学员详情无论成绩状态均不得返回。
      vo.setReviewComment(null);
      vo.setItems(items.getOrDefault(answer.getExamAnswerId(), List.of()));
      return vo;
    }).collect(Collectors.toMap(ExamAnswerVO::getAttemptQuestionId, Function.identity()));
  }

  private void applyResultVisibility(ExamLearnerTaskVO vo, ExamAttempt attempt, ExamCandidate candidate,
                                     Exam exam, ExamPolicy policy) {
    if (isStandaloneBankPractice(attempt)) {
      boolean effective = isCourseEffective(attempt);
      vo.setScorePublishStatus(effective ? "EFFECTIVE" : "UNPUBLISHED");
      vo.setScoreVisible(effective);
      if (effective) {
        vo.setScorePublishedTime(attempt.getResultEffectiveTime());
        vo.setFinalScore(attempt.getFinalScore());
        // 题库练习不产生及格结论。
        vo.setIsPassed(null);
      }
      return;
    }
    boolean published = isPublished(attempt);
    boolean scoreVisible = published && isScoreVisible(policy);
    vo.setScorePublishStatus(published ? "PUBLISHED" : "UNPUBLISHED");
    vo.setScoreVisible(scoreVisible);
    if (scoreVisible) {
      vo.setScorePublishedTime(attempt.getScorePublishedTime());
      vo.setFinalScore(attempt.getFinalScore());
      vo.setIsPassed(attempt.getIsPassed());
    }
  }

  private boolean isPublished(ExamAttempt attempt) {
    return attempt != null && "PUBLISHED".equals(attempt.getAttemptStatus())
      && attempt.getScorePublishedTime() != null;
  }

  private boolean isCourseSource(AssessmentExecution execution) {
    return execution != null && ("COURSE_QUIZ".equals(execution.getSourceType())
      || "COURSE_BANK_PRACTICE".equals(execution.getSourceType()));
  }

  private boolean isCourseEffective(ExamAttempt attempt) {
    return attempt != null && "RESULT_EFFECTIVE".equals(attempt.getAttemptStatus())
      && attempt.getResultEffectiveTime() != null;
  }

  /** 仅独立考试和独立题库练习向答题端下发考试防作弊配置。 */
  /** 从不可变策略快照读取开关；历史快照缺字段时兼容发布时已有策略值。 */
  private int snapshotPolicySwitch(AssessmentExecution execution, String field, Integer fallback) {
    if (execution == null || !StringUtils.hasText(execution.getPolicySnapshotJson())) return fallback == null ? 0 : fallback;
    try {
      return OBJECT_MAPPER.readTree(execution.getPolicySnapshotJson()).path(field).asInt(fallback == null ? 0 : fallback);
    } catch (Exception ignored) {
      return fallback == null ? 0 : fallback;
    }
  }

  /** 从服务端冻结答案快照计算空位数，向学员端只暴露数量而不暴露答案内容。 */
  private Integer resolveBlankCount(ExamAttemptQuestion question) {
    if (!"FILL".equals(question.getQuestionType())) return null;
    try {
      JsonNode blanks = OBJECT_MAPPER.readTree(question.getCorrectAnswerSnapshotJson()).path("blanks");
      return blanks.isArray() && !blanks.isEmpty() ? blanks.size() : 1;
    } catch (Exception ignored) {
      return 1;
    }
  }

  /** 独立题库练习交卷即生效，不应按正式考试等待成绩发布。 */
  private boolean isStandaloneBankPractice(ExamAttempt attempt) {
    return attempt != null && "STANDALONE_PRACTICE".equals(attempt.getSourceType())
      && "BANK_PRACTICE".equals(attempt.getAssessmentKind());
  }

  private boolean isScoreVisible(ExamPolicy policy) {
    return policy == null || !Objects.equals(policy.getIsScoreVisible(), 0);
  }

  /**
   * 答案详情是否对学员可见。
   * <p>
   * 契约取值 {@code SUBMITTED}（交卷后可见）、{@code PUBLISHED}（成绩发布后可见）、{@code NEVER}（不可见）
   * 必须全部生效；{@code PASSED}/{@code EXAM_ENDED}/{@code RETAKE_ENDED} 为历史数据兼容分支，未写入契约。
   *
   * @param published 当前作答成绩是否已发布
   */
  private boolean answerDetailsVisible(ExamAttempt attempt, ExamCandidate candidate, Exam exam, ExamPolicy policy,
                                       boolean published) {
    String mode = policy == null || !StringUtils.hasText(policy.getAnswerDetailMode())
      ? "NEVER" : policy.getAnswerDetailMode();
    LocalDateTime now = LocalDateTime.now();
    return switch (mode) {
      case "SUBMITTED" -> attempt.getSubmittedTime() != null;
      case "PUBLISHED" -> published;
      case "PASSED" -> "PASSED".equals(candidate.getCandidateStatus());
      case "EXAM_ENDED" -> exam.getOpenEndTime() != null && !now.isBefore(exam.getOpenEndTime());
      case "RETAKE_ENDED" -> retakeEndedVisible(exam, candidate, policy, now);
      default -> false;
    };
  }

  private boolean retakeEndedVisible(Exam exam, ExamCandidate candidate, ExamPolicy policy,
                                     LocalDateTime now) {
    if (policy != null && Objects.equals(policy.getIsRetakeEnabled(), 1)
      && policy.getRetakeEndTime() != null) {
      return !now.isBefore(policy.getRetakeEndTime());
    }
    return (exam.getOpenEndTime() != null && !now.isBefore(exam.getOpenEndTime()))
      || policy == null || policy.getIsRetakeEnabled() == null || policy.getIsRetakeEnabled() == 0
      || candidate.getAttemptCount() >= (policy.getRetakeMaxCount() == null ? 0 : policy.getRetakeMaxCount()) + 1;
  }

  private String taskStatus(ExamAttempt attempt, boolean offlineResult) {
    // 线下考试在管理端录分前没有作答记录，此时应显示“等待结果”而非“未开始”。
    if (attempt == null) return offlineResult ? "WAITING_RESULT" : "NOT_STARTED";
    if ("ANSWERING".equals(attempt.getAttemptStatus()) || "CREATED".equals(attempt.getAttemptStatus()))
      return "IN_PROGRESS";
    if (isStandaloneBankPractice(attempt) && isCourseEffective(attempt)) return "RESULT_PUBLISHED";
    if (isPublished(attempt)) return "RESULT_PUBLISHED";
    return "WAITING_RESULT";
  }

  private String actionFor(ExamAttempt attempt, String scorePublishStatus, boolean offlineResult) {
    // 线下考试未出成绩前不提供任何入口动作，避免学员进入无题目的在线作答。
    if (attempt == null) return offlineResult ? null : "START";
    if ("ANSWERING".equals(attempt.getAttemptStatus()) || "CREATED".equals(attempt.getAttemptStatus()))
      return "CONTINUE";
    if ("EFFECTIVE".equals(scorePublishStatus) && isStandaloneBankPractice(attempt)) return "VIEW_RESULT";
    if ("PUBLISHED".equals(scorePublishStatus)) return "VIEW_RESULT";
    return "WAIT_REVIEW";
  }

  private void hideAttemptScores(ExamAttemptVO vo) {
    vo.setObjectiveScore(null);
    vo.setSubjectiveScore(null);
    vo.setFinalScore(null);
    vo.setIsPassed(null);
    vo.setCorrectCount(null);
    vo.setWrongCount(null);
  }

  private void hideAnswerScores(ExamAnswerVO vo) {
    vo.setAutoScore(null);
    vo.setManualScore(null);
    vo.setFinalScore(null);
    vo.setIsCorrect(null);
  }

  private void hideAttemptDetailScores(ExamAttemptVO vo) {
    vo.setObjectiveScore(null);
    vo.setSubjectiveScore(null);
    vo.setCorrectCount(null);
    vo.setWrongCount(null);
  }
}
