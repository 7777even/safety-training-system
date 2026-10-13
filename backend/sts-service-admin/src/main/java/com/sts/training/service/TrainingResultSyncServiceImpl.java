package com.sts.training.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.sts.certificate.service.CertificateAutoIssueService;
import com.sts.common.exception.BusinessException;
import com.sts.training.dto.TrainingContentExecutionConfig;
import com.sts.training.entity.*;
import com.sts.training.mapper.*;
import com.sts.training.port.TrainingAssessmentExecutionPort;
import com.sts.training.port.TrainingAssessmentResult;
import com.sts.training.port.TrainingCourseExecutionPort;
import com.sts.training.port.TrainingCourseResult;
import com.sts.training.vo.TrainingParticipantResultItemVO;
import com.sts.training.vo.TrainingParticipantResultsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 从课程、考试和线下正式事实同步逐内容投影，并在结束时冻结规范化快照。
 */
@Service
@RequiredArgsConstructor
public class TrainingResultSyncServiceImpl implements TrainingResultSyncService {

  static final DateTimeFormatter SNAPSHOT_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

  private static final List<String> TERMINAL = List.of("COMPLETED", "FAILED", "EXPIRED");
  private static final Set<String> STARTED_CONTENT_STATUSES = Set.of(
    "IN_PROGRESS", "PENDING_REVIEW", "PENDING_PUBLISH", "COMPLETED", "FAILED", "EXPIRED");

  private final TrainingPlanMapper planMapper;
  private final TrainingParticipantMapper participantMapper;
  private final TrainingPlanStageMapper stageMapper;
  private final TrainingPlanContentMapper contentMapper;
  private final TrainingExecutionLinkMapper executionLinkMapper;
  private final TrainingOfflineSessionMapper offlineSessionMapper;
  private final TrainingOfflineScoreMapper offlineScoreMapper;
  private final TrainingParticipantContentResultMapper resultMapper;
  private final TrainingCourseExecutionPort coursePort;
  private final TrainingAssessmentExecutionPort assessmentPort;
  private final TrainingContentConfigCodec configCodec;
  private final ObjectMapper objectMapper;
  private final CompositeScoreService compositeScoreService;
  private final CertificateAutoIssueService certificateAutoIssueService;

  @Override
  public void ensurePending(Long trainingPlanId, TrainingPlanContent content, Long executionId,
                            List<TrainingParticipant> participants) {
    if (content == null || participants == null || participants.isEmpty()) {
      return;
    }
    for (TrainingParticipant participant : participants) {
      TrainingParticipantContentResult existing = findProjection(
        participant.getTrainingParticipantId(), content.getPlanContentId());
      if (existing != null) {
        continue;
      }
      TrainingParticipantContentResult row = new TrainingParticipantContentResult();
      row.setTrainingPlanId(trainingPlanId);
      row.setTrainingParticipantId(participant.getTrainingParticipantId());
      row.setPlanContentId(content.getPlanContentId());
      row.setContentType(content.getContentType());
      row.setExecutionId(executionId);
      row.setResultStatus("PENDING");
      row.setEffectiveDurationSeconds(0);
      row.setResultRevision(0);
      try {
        resultMapper.insert(row);
      } catch (DuplicateKeyException ignored) {
        // 并发下已有投影行。
      }
    }
  }

  @Override
  @Transactional
  public List<TrainingParticipantResultsVO> synchronize(Long trainingPlanId) {
    requirePlan(trainingPlanId);
    List<TrainingPlanContent> contents = contents(trainingPlanId);
    List<TrainingParticipantResultsVO> results = new ArrayList<>();
    for (TrainingParticipant participant : participants(trainingPlanId)) {
      if (participant.getResultFinalizedTime() != null) {
        results.add(readFinalizedResults(trainingPlanId, participant, contents));
        continue;
      }
      synchronizeParticipant(participant, contents);
      results.add(toResults(trainingPlanId, participant, contents));
    }
    return results;
  }

  @Override
  @Transactional
  public void synchronizeParticipant(Long trainingParticipantId) {
    if (trainingParticipantId == null) {
      return;
    }
    TrainingParticipant participant = requireParticipant(trainingParticipantId);
    if (participant.getResultFinalizedTime() != null) {
      return;
    }
    requirePlan(participant.getTrainingPlanId());
    synchronizeParticipant(participant, contents(participant.getTrainingPlanId()));
  }

  @Override
  @Transactional
  public void synchronizeParticipantContent(Long trainingParticipantId, Long planContentId) {
    if (trainingParticipantId == null || planContentId == null) {
      return;
    }
    TrainingParticipant participant = requireParticipant(trainingParticipantId);
    if (participant.getResultFinalizedTime() != null) {
      return;
    }
    TrainingPlanContent content = requireContentOfPlan(participant.getTrainingPlanId(), planContentId);
    requirePlan(participant.getTrainingPlanId());
    upsertProjection(participant, content);
    List<TrainingPlanContent> contents = contents(participant.getTrainingPlanId());
    TrainingParticipantResultsVO derived = toResults(participant.getTrainingPlanId(), participant, contents);
    applyAggregates(participant, derived);
    markLearningWhenContentStarted(participant, derived);
    participantMapper.updateById(participant);
  }

  /**
   * 将一个未冻结参训人的全部内容事实重建为投影与汇总，供计划补账和综合成绩计算共同复用。
   */
  private void synchronizeParticipant(TrainingParticipant participant, List<TrainingPlanContent> contents) {
    for (TrainingPlanContent content : contents) {
      upsertProjection(participant, content);
    }
    TrainingParticipantResultsVO derived = toResults(participant.getTrainingPlanId(), participant, contents);
    applyAggregates(participant, derived);
    markLearningWhenContentStarted(participant, derived);
    participantMapper.updateById(participant);
  }

  @Override
  public TrainingParticipantResultsVO getResults(Long trainingPlanId, Long trainingParticipantId) {
    requirePlan(trainingPlanId);
    TrainingParticipant participant = requireParticipant(trainingParticipantId);
    if (!trainingPlanId.equals(participant.getTrainingPlanId())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 8120, "参训人不属于指定计划");
    }
    return readFinalizedResults(trainingPlanId, participant, contents(trainingPlanId));
  }

  /** 统计 GET 复用原有来源判定，仅建立内存投影，不触发同步写入。 */
  @Override
  public TrainingParticipantResultsVO getCurrentResults(Long trainingPlanId, Long trainingParticipantId) {
    var plan=requirePlan(trainingPlanId);
    var participant=requireParticipant(trainingParticipantId);
    if(!trainingPlanId.equals(participant.getTrainingPlanId()))throw new BusinessException(HttpStatus.BAD_REQUEST,8120,"参训人不属于指定计划");
    var contents=contents(trainingPlanId);
    if(participant.getResultFinalizedTime()!=null||Set.of("ENDED","ARCHIVED").contains(plan.getPublishStatus()))return readFinalizedResults(trainingPlanId,participant,contents);
    if("DRAFT".equals(plan.getPublishStatus())) {
      var draft=toResults(trainingPlanId,participant,contents,List.of());
      draft.setResultStatus("INCOMPLETE");return draft;
    }
    List<TrainingParticipantContentResult> rows=new ArrayList<>();
    for(var content:contents) {
      var row=new TrainingParticipantContentResult();row.setPlanContentId(content.getPlanContentId());row.setContentType(content.getContentType());
      applyDraft(row,readDraft(participant,content),true);rows.add(row);
    }
    return toResults(trainingPlanId,participant,contents,rows);
  }

  @Override
  @Transactional
  public void freezePlan(Long trainingPlanId) {
    TrainingPlan plan = requirePlan(trainingPlanId);
    List<TrainingPlanContent> contents = contents(trainingPlanId);
    LocalDateTime now = LocalDateTime.now();
    for (TrainingParticipant participant : participants(trainingPlanId)) {
      if (participant.getResultFinalizedTime() != null
        && participant.getFinalResultSnapshotJson() != null
        && !participant.getFinalResultSnapshotJson().isBlank()) {
        continue;
      }
      List<TrainingParticipantContentResult> rows = resultMapper.selectList(
        new LambdaQueryWrapper<TrainingParticipantContentResult>()
          .eq(TrainingParticipantContentResult::getTrainingParticipantId,
              participant.getTrainingParticipantId())
          .orderByAsc(TrainingParticipantContentResult::getPlanContentId));
      for (TrainingParticipantContentResult row : rows) {
        row.setFinalizedTime(now);
        resultMapper.updateById(row);
      }
      String resultStatus = decideParticipantStatus(contents, rows);
      participant.setResultStatus(resultStatus);
      if ("PASSED".equals(resultStatus)) {
        participant.setParticipantStatus("COMPLETED");
        participant.setCompletedTime(now);
      } else if ("FAILED".equals(resultStatus)) {
        participant.setParticipantStatus("FAILED");
      } else {
        participant.setParticipantStatus("FAILED");
      }
      TrainingParticipantResultsVO derived = toResults(trainingPlanId, participant, contents);
      applyAggregates(participant, derived);
      participant.setResultFinalizedTime(now);
      String json = snapshotJson(plan, participant, contents, rows, resultStatus);
      participant.setFinalResultSnapshotJson(json);
      participant.setFinalResultSnapshotHash(sha256Hex(json));
      participantMapper.updateById(participant);
      if ("PASSED".equals(resultStatus)) {
        // 培训结果合格才以培训计划为主数据来源签发；计划内课程/考试内容不单独发证。
        certificateAutoIssueService.issueForSource("TRAINING", participant.getTrainingPlanId(),
                                                   participant.getUserId(), participant.getTrainingParticipantId());
      }
    }
  }

  private void upsertProjection(TrainingParticipant participant, TrainingPlanContent content) {
    TrainingParticipantContentResult existing = findProjection(
      participant.getTrainingParticipantId(), content.getPlanContentId());
    if (existing != null && existing.getFinalizedTime() != null) {
      return;
    }
    ProjectionDraft draft = readDraft(participant, content);
    if (existing == null) {
      TrainingParticipantContentResult row = new TrainingParticipantContentResult();
      row.setTrainingPlanId(participant.getTrainingPlanId());
      row.setTrainingParticipantId(participant.getTrainingParticipantId());
      row.setPlanContentId(content.getPlanContentId());
      row.setContentType(content.getContentType());
      applyDraft(row, draft, true);
      row.setResultRevision(draft.changedFromEmpty() ? 1 : 0);
      row.setSynchronizedTime(LocalDateTime.now());
      try {
        resultMapper.insert(row);
      } catch (DuplicateKeyException ex) {
        TrainingParticipantContentResult concurrent = findProjection(
          participant.getTrainingParticipantId(), content.getPlanContentId());
        if (concurrent != null) {
          mergeExisting(concurrent, draft);
        }
      }
      return;
    }
    mergeExisting(existing, draft);
  }

  private void mergeExisting(TrainingParticipantContentResult existing, ProjectionDraft draft) {
    boolean changed = applyDraft(existing, draft, false);
    if (changed) {
      existing.setResultRevision(existing.getResultRevision() == null ? 1 : existing.getResultRevision() + 1);
    }
    existing.setSynchronizedTime(LocalDateTime.now());
    resultMapper.updateById(existing);
  }

  private boolean applyDraft(TrainingParticipantContentResult row, ProjectionDraft draft, boolean inserting) {
    boolean changed = false;
    if (!Objects.equals(row.getExecutionId(), draft.executionId)) {
      row.setExecutionId(draft.executionId);
      changed = true;
    }
    if (draft.sourceRecordType != null && !Objects.equals(row.getSourceRecordType(), draft.sourceRecordType)) {
      row.setSourceRecordType(draft.sourceRecordType);
      changed = true;
    }
    if (draft.sourceRecordId != null && !Objects.equals(row.getSourceRecordId(), draft.sourceRecordId)) {
      row.setSourceRecordId(draft.sourceRecordId);
      changed = true;
    }
    if (draft.finalAttemptId != null && !Objects.equals(row.getFinalAttemptId(), draft.finalAttemptId)) {
      row.setFinalAttemptId(draft.finalAttemptId);
      changed = true;
    }
    if (draft.evaluatedCount != null && !Objects.equals(row.getEvaluatedCount(), draft.evaluatedCount)) {
      row.setEvaluatedCount(draft.evaluatedCount);
      changed = true;
    }
    if (draft.correctCount != null && !Objects.equals(row.getCorrectCount(), draft.correctCount)) {
      row.setCorrectCount(draft.correctCount);
      changed = true;
    }
    if (draft.wrongCount != null && !Objects.equals(row.getWrongCount(), draft.wrongCount)) {
      row.setWrongCount(draft.wrongCount);
      changed = true;
    }
    if (draft.unassessedCount != null && !Objects.equals(row.getUnassessedCount(), draft.unassessedCount)) {
      row.setUnassessedCount(draft.unassessedCount);
      changed = true;
    }
    if (draft.evaluatedCount != null && !sameDecimal(row.getAccuracyRate(), draft.accuracyRate)) {
      row.setAccuracyRate(draft.accuracyRate);
      changed = true;
    }
    if (draft.sourceStatus != null && !Objects.equals(row.getSourceStatus(), draft.sourceStatus)) {
      row.setSourceStatus(draft.sourceStatus);
      changed = true;
    }
    String nextStatus = nextStatus(row.getResultStatus(), draft.resultStatus);
    if (!Objects.equals(row.getResultStatus(), nextStatus)) {
      row.setResultStatus(nextStatus);
      changed = true;
    }
    int duration = draft.durationSeconds == null ? 0 : draft.durationSeconds;
    if (!Objects.equals(row.getEffectiveDurationSeconds(), duration)) {
      row.setEffectiveDurationSeconds(duration);
      changed = true;
    }
    if (draft.score != null && (row.getScore() == null || row.getScore().compareTo(draft.score) != 0)) {
      row.setScore(draft.score);
      changed = true;
    }
    if (draft.completedTime != null && !Objects.equals(row.getCompletedTime(), draft.completedTime)) {
      row.setCompletedTime(draft.completedTime);
      changed = true;
    }
    if (draft.sourceUpdatedTime != null && !Objects.equals(row.getSourceUpdatedTime(), draft.sourceUpdatedTime)) {
      row.setSourceUpdatedTime(draft.sourceUpdatedTime);
      changed = true;
    }
    if (inserting && row.getResultStatus() == null) {
      row.setResultStatus("PENDING");
    }
    if (row.getEffectiveDurationSeconds() == null) {
      row.setEffectiveDurationSeconds(0);
    }
    return changed;
  }

  private ProjectionDraft readDraft(TrainingParticipant participant, TrainingPlanContent content) {
    ProjectionDraft draft = new ProjectionDraft();
    if ("COURSE".equals(content.getContentType())) {
      TrainingExecutionLink link = findLink(content.getPlanContentId(), "COURSE");
      if (link == null) {
        return draft;
      }
      draft.executionId = link.getExecutionId();
      TrainingCourseResult enrollment = coursePort.readResult(
        link.getExecutionId(), participant.getTrainingParticipantId());
      if (enrollment == null) {
        draft.resultStatus = "PENDING";
        return draft;
      }
      draft.sourceRecordType = "COURSE_ENROLLMENT";
      draft.sourceRecordId = enrollment.getSourceRecordId();
      draft.sourceStatus = enrollment.getSourceStatus();
      draft.durationSeconds = enrollment.getStudySeconds() == null ? 0 : enrollment.getStudySeconds();
      draft.completedTime = enrollment.getCompletedTime();
      draft.sourceUpdatedTime = enrollment.getCompletedTime();
      draft.resultStatus = mapCourseStatus(enrollment.getSourceStatus());
      if ("COMPLETED".equals(draft.resultStatus)) {
        draft.score = enrollment.getScore();
      }
      return draft;
    }
    if ("EXAM".equals(content.getContentType()) || "QUESTION_BANK".equals(content.getContentType())) {
      TrainingExecutionLink link = findLink(content.getPlanContentId(), content.getContentType());
      if (link == null) {
        return draft;
      }
      draft.executionId = link.getExecutionId();
      TrainingAssessmentResult assessment = assessmentPort.readResult(
        link.getExecutionId(), participant.getTrainingParticipantId());
      if (assessment == null) {
        draft.resultStatus = "PENDING";
        return draft;
      }
      draft.sourceRecordType = "EXAM_CANDIDATE";
      draft.sourceRecordId = assessment.getSourceRecordId();
      draft.sourceStatus = assessment.getSourceStatus();
      draft.finalAttemptId = assessment.getFinalAttemptId();
      draft.evaluatedCount = assessment.getEvaluatedCount();
      draft.correctCount = assessment.getCorrectCount();
      draft.wrongCount = assessment.getWrongCount();
      draft.unassessedCount = assessment.getUnassessedCount();
      draft.accuracyRate = assessment.getAccuracyRate();
      // 考试/题库不计入培训获得学时；作答时限不是学时。
      draft.durationSeconds = 0;
      draft.sourceUpdatedTime = assessment.getCompletedTime();
      if ("QUESTION_BANK".equals(content.getContentType())
        && "COMPLETED".equals(assessment.getSourceStatus())) {
        draft.resultStatus = "COMPLETED";
        draft.completedTime = assessment.getCompletedTime();
        draft.score = null;
      } else if (assessment.getScore() != null) {
        draft.score = assessment.getScore();
        draft.completedTime = assessment.getCompletedTime();
        if ("PASSED".equals(assessment.getSourceStatus())) {
          draft.resultStatus = "COMPLETED";
        } else if ("FAILED".equals(assessment.getSourceStatus())) {
          draft.resultStatus = "FAILED";
        } else {
          draft.resultStatus = "PENDING_PUBLISH";
        }
      } else if ("EXPIRED".equals(assessment.getSourceStatus()) || "FAILED".equals(assessment.getSourceStatus())) {
        draft.resultStatus = assessment.getSourceStatus();
      } else if ("PENDING_REVIEW".equals(assessment.getSourceStatus())) {
        // 主观题尚未完成教师批阅，不能回退为待开始，也不能提前视为待发布成绩。
        draft.resultStatus = "PENDING_REVIEW";
      } else if ("IN_PROGRESS".equals(assessment.getSourceStatus())
        || "SUBMITTED".equals(assessment.getSourceStatus())
        || "GRADED".equals(assessment.getSourceStatus())) {
        draft.resultStatus = "SUBMITTED".equals(assessment.getSourceStatus())
          || "GRADED".equals(assessment.getSourceStatus())
          ? "PENDING_PUBLISH" : "IN_PROGRESS";
      } else {
        draft.resultStatus = "PENDING";
      }
      return draft;
    }
    if ("OFFLINE".equals(content.getContentType())) {
      TrainingOfflineSession session = findSession(content.getPlanContentId());
      if (session == null) {
        return draft;
      }
      draft.executionId = session.getOfflineSessionId();
      TrainingOfflineScore score = offlineScoreMapper.selectOne(
        new LambdaQueryWrapper<TrainingOfflineScore>()
          .eq(TrainingOfflineScore::getOfflineSessionId, session.getOfflineSessionId())
          .eq(TrainingOfflineScore::getTrainingParticipantId, participant.getTrainingParticipantId())
          .isNotNull(TrainingOfflineScore::getScoredBy)
          .orderByDesc(TrainingOfflineScore::getScoredTime)
          .last("LIMIT 1"));
      if (score == null) {
        draft.resultStatus = "PENDING";
        draft.durationSeconds = 0;
        return draft;
      }
      draft.sourceRecordType = "OFFLINE_SCORE";
      draft.sourceRecordId = score.getOfflineScoreId();
      draft.sourceStatus = "SCORED";
      draft.score = score.getScore();
      draft.completedTime = score.getScoredTime();
      draft.sourceUpdatedTime = score.getScoredTime();
      if (failedByPassScore(content, score.getScore())) {
        draft.resultStatus = "FAILED";
        draft.durationSeconds = 0;
      } else {
        draft.resultStatus = "COMPLETED";
        draft.durationSeconds = requiredHoursSeconds(content);
      }
    }
    return draft;
  }

  /**
   * 线下获得秒数：COMPLETED 时为内容安排学时换算，否则 0。
   */
  private int requiredHoursSeconds(TrainingPlanContent content) {
    if (content.getRequiredHours() == null) {
      return 0;
    }
    return content.getRequiredHours()
      .multiply(BigDecimal.valueOf(3600))
      .setScale(0, RoundingMode.HALF_UP)
      .intValue();
  }

  private boolean failedByPassScore(TrainingPlanContent content, BigDecimal score) {
    TrainingContentExecutionConfig config = configCodec.fromJson(content.getExecutionConfigJson());
    if (config == null || config.getPassScore() == null || score == null) {
      return false;
    }
    return score.compareTo(config.getPassScore()) < 0;
  }

  private String mapCourseStatus(String learningStatus) {
    if ("COMPLETED".equals(learningStatus)) {
      return "COMPLETED";
    }
    if ("LEARNING".equals(learningStatus)) {
      return "IN_PROGRESS";
    }
    if ("EXPIRED".equals(learningStatus) || "FAILED".equals(learningStatus)) {
      return learningStatus;
    }
    return "PENDING";
  }

  private String nextStatus(String current, String incoming) {
    if (incoming == null) {
      return current == null ? "PENDING" : current;
    }
    if (current == null) {
      return incoming;
    }
    if (TERMINAL.contains(current) && !TERMINAL.contains(incoming) && !"COMPLETED".equals(incoming)) {
      return current;
    }
    return incoming;
  }

  private void applyAggregates(TrainingParticipant participant, TrainingParticipantResultsVO derived) {
    if (derived.getCourseScore() != null) {
      participant.setCourseScore(derived.getCourseScore());
    }
    if (derived.getExamScore() != null) {
      participant.setExamScore(derived.getExamScore());
    }
    if (derived.getOfflineScore() != null) {
      participant.setOfflineScore(derived.getOfflineScore());
    }
    participant.setOnlineHours(derived.getOnlineHours() == null ? BigDecimal.ZERO : derived.getOnlineHours());
    participant.setOfflineHours(derived.getOfflineHours() == null ? BigDecimal.ZERO : derived.getOfflineHours());
    compositeScoreService.refresh(participant);
  }

  /**
   * 内容投影出现个人执行事实后，推进参训人的过程状态；最终结论仍仅在计划冻结时写入。
   */
  private void markLearningWhenContentStarted(TrainingParticipant participant,
                                              TrainingParticipantResultsVO derived) {
    if (!"PENDING".equals(participant.getParticipantStatus()) || derived.getItems() == null) {
      return;
    }
    boolean started = derived.getItems().stream()
      .map(TrainingParticipantResultItemVO::getResultStatus)
      .anyMatch(STARTED_CONTENT_STATUSES::contains);
    if (started) {
      participant.setParticipantStatus("LEARNING");
    }
  }

  private TrainingParticipantResultsVO readFinalizedResults(Long trainingPlanId, TrainingParticipant participant,
                                                            List<TrainingPlanContent> contents) {
    if (participant.getResultFinalizedTime() != null
      && StringUtils.hasText(participant.getFinalResultSnapshotJson())) {
      assertFinalSnapshotIntact(participant);
      return TrainingFinalResultSnapshotParser.parse(
        participant.getFinalResultSnapshotJson(), participant, contents, objectMapper);
    }
    return toResults(trainingPlanId, participant, contents);
  }

  private void assertFinalSnapshotIntact(TrainingParticipant participant) {
    String json = participant.getFinalResultSnapshotJson();
    String storedHash = participant.getFinalResultSnapshotHash();
    if (!StringUtils.hasText(json)
      || !StringUtils.hasText(storedHash)
      || storedHash.length() != 64
      || !storedHash.equals(sha256Hex(json))) {
      throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, 8111, "最终结果快照完整性校验失败");
    }
  }

  private TrainingParticipantResultsVO toResults(Long trainingPlanId, TrainingParticipant participant,
                                                 List<TrainingPlanContent> contents) {
    List<TrainingParticipantContentResult> rows = resultMapper.selectList(
      new LambdaQueryWrapper<TrainingParticipantContentResult>()
        .eq(TrainingParticipantContentResult::getTrainingParticipantId,
            participant.getTrainingParticipantId()));
    return toResults(trainingPlanId,participant,contents,rows);
  }

  /** 共享逐内容结果及正式完成状态聚合口径。 */
  private TrainingParticipantResultsVO toResults(Long trainingPlanId, TrainingParticipant participant,
      List<TrainingPlanContent> contents, List<TrainingParticipantContentResult> rows) {
    Map<Long, String> offlineSessionStatuses = offlineSessionStatuses(contents);
    List<TrainingParticipantResultItemVO> items = new ArrayList<>();
    int courseSeconds = 0;
    int offlineSeconds = 0;
    List<ScoredItem> courseItems = new ArrayList<>();
    List<ScoredItem> examItems = new ArrayList<>();
    List<ScoredItem> offlineItems = new ArrayList<>();
    for (TrainingPlanContent content : contents) {
      TrainingParticipantContentResult row = rows.stream()
        .filter(item -> content.getPlanContentId().equals(item.getPlanContentId()))
        .findFirst().orElse(null);
      TrainingParticipantResultItemVO item = new TrainingParticipantResultItemVO();
      item.setPlanContentId(content.getPlanContentId());
      item.setContentName(content.getContentName());
      item.setExecutionType(content.getContentType());
      if ("OFFLINE".equals(content.getContentType())) {
        item.setSessionStatus(offlineSessionStatuses.get(content.getPlanContentId()));
      }
      if (row != null) {
        item.setExecutionId(row.getExecutionId());
        item.setSourceRecordType(row.getSourceRecordType());
        item.setSourceRecordId(row.getSourceRecordId());
        item.setFinalAttemptId(row.getFinalAttemptId());
        item.setEvaluatedCount(row.getEvaluatedCount());
        item.setCorrectCount(row.getCorrectCount());
        item.setWrongCount(row.getWrongCount());
        item.setUnassessedCount(row.getUnassessedCount());
        item.setAccuracyRate(row.getAccuracyRate());
        item.setSourceStatus(row.getSourceStatus());
        item.setResultStatus(row.getResultStatus());
        item.setEffectiveDurationSeconds(row.getEffectiveDurationSeconds());
        item.setScore(row.getScore());
        item.setCompletedTime(row.getCompletedTime());
        item.setResultRevision(row.getResultRevision());
        item.setFinalizedTime(row.getFinalizedTime());
        if ("COURSE".equals(content.getContentType())) {
          courseSeconds += row.getEffectiveDurationSeconds() == null ? 0 : row.getEffectiveDurationSeconds();
          addScored(courseItems, content, row.getScore());
        } else if ("EXAM".equals(content.getContentType())) {
          addScored(examItems, content, row.getScore());
        } else if ("OFFLINE".equals(content.getContentType())) {
          offlineSeconds += row.getEffectiveDurationSeconds() == null ? 0 : row.getEffectiveDurationSeconds();
          addScored(offlineItems, content, row.getScore());
        }
      } else {
        item.setResultStatus("PENDING");
        item.setEffectiveDurationSeconds(0);
      }
      items.add(item);
    }
    TrainingParticipantResultsVO result = new TrainingParticipantResultsVO();
    result.setTrainingPlanId(trainingPlanId);
    result.setTrainingParticipantId(participant.getTrainingParticipantId());
    result.setUserId(participant.getUserId());
    result.setCourseScore(aggregate(courseItems));
    result.setExamScore(aggregate(examItems));
    result.setOfflineScore(aggregate(offlineItems));
    result.setCompositeScore(participant.getCompositeScore());
    result.setOfficialCalculationRunId(participant.getOfficialCalculationRunId());
    // 执行中的结果尚未冻结，必须根据当前逐内容投影实时汇总，不能回传仅在结束时才落库的历史字段。
    result.setResultStatus(participant.getResultFinalizedTime() == null
      ? decideParticipantStatus(contents, rows) : participant.getResultStatus());
    result.setOnlineHours(toHours(courseSeconds));
    result.setOfflineHours(toHours(offlineSeconds));
    result.setResultFinalizedTime(participant.getResultFinalizedTime());
    result.setItems(items);
    return result;
  }

  /** 批量读取线下场次状态，避免结果列表按内容项重复查询。 */
  private Map<Long, String> offlineSessionStatuses(List<TrainingPlanContent> contents) {
    List<Long> offlineContentIds = contents.stream()
      .filter(content -> "OFFLINE".equals(content.getContentType()))
      .map(TrainingPlanContent::getPlanContentId)
      .toList();
    if (offlineContentIds.isEmpty()) {
      return Map.of();
    }
    List<TrainingOfflineSession> sessions = offlineSessionMapper.selectList(
      new LambdaQueryWrapper<TrainingOfflineSession>()
        .in(TrainingOfflineSession::getPlanContentId, offlineContentIds));
    Map<Long, String> statuses = new HashMap<>();
    if (sessions != null) {
      sessions.forEach(session -> statuses.put(session.getPlanContentId(), session.getSessionStatus()));
    }
    return statuses;
  }

  private void addScored(List<ScoredItem> items, TrainingPlanContent content, BigDecimal score) {
    if (content.getIsScored() != null && content.getIsScored() == 0) {
      return;
    }
    items.add(new ScoredItem(content.getScoreAggregationMode(), content.getScoreWeight(), score,
                             content.getIsRequired() != null && content.getIsRequired() == 1));
  }

  private BigDecimal aggregate(List<ScoredItem> items) {
    if (items.isEmpty()) {
      return null;
    }
    boolean weighted = items.stream().anyMatch(item -> "WEIGHTED_AVERAGE".equals(item.mode));
    if (weighted) {
      if (items.stream().anyMatch(item -> item.required && item.score == null)) {
        return null;
      }
      BigDecimal weightSum = BigDecimal.ZERO;
      BigDecimal scoreSum = BigDecimal.ZERO;
      for (ScoredItem item : items) {
        if (item.score == null) {
          continue;
        }
        BigDecimal weight = item.weight == null ? BigDecimal.ONE : item.weight;
        if (weight.compareTo(BigDecimal.ZERO) <= 0) {
          continue;
        }
        weightSum = weightSum.add(weight);
        scoreSum = scoreSum.add(item.score.multiply(weight));
      }
      if (weightSum.compareTo(BigDecimal.ZERO) == 0) {
        return null;
      }
      return scoreSum.divide(weightSum, 2, RoundingMode.HALF_UP);
    }
    List<BigDecimal> scores = items.stream().map(item -> item.score).filter(Objects::nonNull).toList();
    if (scores.isEmpty()) {
      return null;
    }
    BigDecimal sum = scores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    return sum.divide(BigDecimal.valueOf(scores.size()), 2, RoundingMode.HALF_UP);
  }

  private BigDecimal toHours(int seconds) {
    return BigDecimal.valueOf(seconds).divide(BigDecimal.valueOf(3600), 2, RoundingMode.HALF_UP);
  }

  String decideParticipantStatus(List<TrainingPlanContent> contents,
                                 List<TrainingParticipantContentResult> rows) {
    boolean anyFailed = false;
    boolean anyIncomplete = false;
    for (TrainingPlanContent content : contents) {
      if (content.getIsRequired() == null || content.getIsRequired() != 1) {
        continue;
      }
      TrainingParticipantContentResult row = rows.stream()
        .filter(item -> content.getPlanContentId().equals(item.getPlanContentId()))
        .findFirst().orElse(null);
      if (row == null || !TERMINAL.contains(row.getResultStatus())) {
        anyIncomplete = true;
        continue;
      }
      if ("FAILED".equals(row.getResultStatus())) {
        anyFailed = true;
      } else if ("EXPIRED".equals(row.getResultStatus())) {
        anyIncomplete = true;
      }
    }
    if (anyFailed) {
      return "FAILED";
    }
    if (anyIncomplete) {
      return "INCOMPLETE";
    }
    return "PASSED";
  }

  private String snapshotJson(TrainingPlan plan, TrainingParticipant participant,
                              List<TrainingPlanContent> contents,
                              List<TrainingParticipantContentResult> rows, String resultStatus) {
    TrainingFinalResultSnapshot snapshot = new TrainingFinalResultSnapshot();
    snapshot.setSchemaVersion(3);
    snapshot.setTrainingPlanId(plan.getTrainingPlanId());
    snapshot.setTrainingParticipantId(participant.getTrainingParticipantId());
    snapshot.setResultStatus(resultStatus);
    snapshot.setFinalizedTime(SNAPSHOT_TIME.format(participant.getResultFinalizedTime() == null
                                                     ? LocalDateTime.now() : participant.getResultFinalizedTime()));
    snapshot.setOnlineHours(decimalText(participant.getOnlineHours()));
    snapshot.setOfflineHours(decimalText(participant.getOfflineHours()));
    snapshot.setCourseScore(decimalText(participant.getCourseScore()));
    snapshot.setExamScore(decimalText(participant.getExamScore()));
    snapshot.setOfflineScore(decimalText(participant.getOfflineScore()));
    snapshot.setCompositeScore(decimalText(participant.getCompositeScore()));
    snapshot.setOfficialCalculationRunId(participant.getOfficialCalculationRunId());
    List<TrainingFinalResultSnapshotItem> items = new ArrayList<>();
    List<TrainingPlanContent> ordered = contents.stream()
      .sorted(Comparator.comparing(TrainingPlanContent::getPlanContentId)).toList();
    for (TrainingPlanContent content : ordered) {
      TrainingParticipantContentResult row = rows.stream()
        .filter(item -> content.getPlanContentId().equals(item.getPlanContentId()))
        .findFirst().orElse(null);
      TrainingFinalResultSnapshotItem item = new TrainingFinalResultSnapshotItem();
      item.setPlanContentId(content.getPlanContentId());
      item.setContentType(content.getContentType());
      if (row != null) {
        item.setExecutionId(row.getExecutionId());
        item.setSourceRecordType(row.getSourceRecordType());
        item.setSourceRecordId(row.getSourceRecordId());
        item.setFinalAttemptId(row.getFinalAttemptId());
        item.setEvaluatedCount(row.getEvaluatedCount());
        item.setCorrectCount(row.getCorrectCount());
        item.setWrongCount(row.getWrongCount());
        item.setUnassessedCount(row.getUnassessedCount());
        item.setAccuracyRate(decimalText(row.getAccuracyRate()));
        item.setSourceStatus(row.getSourceStatus());
        item.setResultStatus(row.getResultStatus());
        item.setEffectiveDurationSeconds(row.getEffectiveDurationSeconds());
        item.setScore(decimalText(row.getScore()));
        item.setCompletedTime(row.getCompletedTime() == null ? null : SNAPSHOT_TIME.format(row.getCompletedTime()));
      }
      items.add(item);
    }
    snapshot.setItems(items);
    try {
      return objectMapper.copy()
        .disable(SerializationFeature.INDENT_OUTPUT)
        .writeValueAsString(snapshot);
    } catch (JsonProcessingException ex) {
      throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, 8111, "快照序列化失败");
    }
  }

  static String decimalText(BigDecimal value) {
    if (value == null) {
      return null;
    }
    return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
  }

  private static boolean sameDecimal(BigDecimal first, BigDecimal second) {
    return first == null ? second == null : second != null && first.compareTo(second) == 0;
  }

  static String sha256Hex(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(hash.length * 2);
      for (byte b : hash) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (Exception ex) {
      throw new IllegalStateException("SHA-256 不可用", ex);
    }
  }

  private TrainingParticipantContentResult findProjection(Long participantId, Long contentId) {
    return resultMapper.selectOne(new LambdaQueryWrapper<TrainingParticipantContentResult>()
                                    .eq(TrainingParticipantContentResult::getTrainingParticipantId, participantId)
                                    .eq(TrainingParticipantContentResult::getPlanContentId, contentId));
  }

  private TrainingExecutionLink findLink(Long contentId, String type) {
    return executionLinkMapper.selectOne(new LambdaQueryWrapper<TrainingExecutionLink>()
                                           .eq(TrainingExecutionLink::getPlanContentId, contentId)
                                           .eq(TrainingExecutionLink::getExecutionType, type));
  }

  private TrainingOfflineSession findSession(Long contentId) {
    return offlineSessionMapper.selectOne(new LambdaQueryWrapper<TrainingOfflineSession>()
                                            .eq(TrainingOfflineSession::getPlanContentId, contentId)
                                            .last("LIMIT 1"));
  }

  private List<TrainingPlanContent> contents(Long trainingPlanId) {
    List<Long> stageIds = stageMapper.selectList(new LambdaQueryWrapper<TrainingPlanStage>()
                                                   .eq(TrainingPlanStage::getTrainingPlanId, trainingPlanId))
      .stream().map(TrainingPlanStage::getPlanStageId).toList();
    if (stageIds.isEmpty()) {
      return List.of();
    }
    return contentMapper.selectList(new LambdaQueryWrapper<TrainingPlanContent>()
                                      .in(TrainingPlanContent::getPlanStageId, stageIds)
                                      .orderByAsc(TrainingPlanContent::getSortOrder));
  }

  private List<TrainingParticipant> participants(Long trainingPlanId) {
    return participantMapper.selectList(new LambdaQueryWrapper<TrainingParticipant>()
                                          .eq(TrainingParticipant::getTrainingPlanId, trainingPlanId)
                                          .orderByAsc(TrainingParticipant::getTrainingParticipantId));
  }

  private TrainingPlan requirePlan(Long id) {
    TrainingPlan plan = planMapper.selectById(id);
    if (plan == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 8101, "培训计划不存在");
    }
    return plan;
  }

  private TrainingParticipant requireParticipant(Long id) {
    TrainingParticipant participant = participantMapper.selectById(id);
    if (participant == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 8110, "参训人不存在");
    }
    return participant;
  }

  private TrainingPlanContent requireContentOfPlan(Long trainingPlanId, Long planContentId) {
    TrainingPlanContent content = contentMapper.selectById(planContentId);
    if (content == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 8144, "培训计划内容不存在");
    }
    TrainingPlanStage stage = stageMapper.selectById(content.getPlanStageId());
    if (stage == null || !trainingPlanId.equals(stage.getTrainingPlanId())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 8120, "计划内容不属于指定培训计划");
    }
    return content;
  }

  private static final class ProjectionDraft {
    Long executionId;
    String sourceRecordType;
    Long sourceRecordId;
    Long finalAttemptId;
    Integer evaluatedCount;
    Integer correctCount;
    Integer wrongCount;
    Integer unassessedCount;
    BigDecimal accuracyRate;
    String sourceStatus;
    String resultStatus;
    Integer durationSeconds;
    BigDecimal score;
    LocalDateTime completedTime;
    LocalDateTime sourceUpdatedTime;

    boolean changedFromEmpty() {
      return sourceRecordId != null || score != null || (durationSeconds != null && durationSeconds > 0);
    }
  }

  private record ScoredItem(String mode, BigDecimal weight, BigDecimal score, boolean required) {
  }
}
