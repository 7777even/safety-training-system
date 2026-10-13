package com.sts.training.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.common.exception.BusinessException;
import com.sts.training.dto.TrainingContentExecutionConfig;
import com.sts.training.dto.TrainingExamPolicySnapshot;
import com.sts.training.dto.TrainingExamResourceSnapshot;
import com.sts.training.entity.*;
import com.sts.training.mapper.*;
import com.sts.training.port.*;
import com.sts.training.vo.TrainingExecutionLinkVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 单内容执行生成。使用独立事务，供计划级生成逐条调用。
 */
@Service
@RequiredArgsConstructor
public class TrainingExecutionProvisioner {

  private final TrainingPlanMapper planMapper;
  private final TrainingPlanStageMapper stageMapper;
  private final TrainingPlanContentMapper contentMapper;
  private final TrainingParticipantMapper participantMapper;
  private final TrainingExecutionLinkMapper executionLinkMapper;
  private final TrainingOfflineSessionMapper offlineSessionMapper;
  private final TrainingCourseExecutionPort coursePort;
  private final TrainingAssessmentExecutionPort assessmentPort;
  private final TrainingContentConfigCodec configCodec;
  private final TrainingResultSyncService resultSyncService;
  private final TrainingQuestionPoolSnapshotService questionPoolSnapshotService;
  private final ObjectMapper objectMapper;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public TrainingExecutionLinkVO provision(Long trainingPlanId, Long planContentId) {
    TrainingPlan plan = requirePlan(trainingPlanId);
    if (!List.of("PUBLISHED", "RUNNING").contains(plan.getPublishStatus())) {
      throw conflict(8140, "仅已发布或进行中的计划可补建执行实例");
    }
    TrainingPlanContent content = requireContent(trainingPlanId, planContentId);
    TrainingPlanStage stage = stageMapper.selectById(content.getPlanStageId());
    List<TrainingParticipantBinding> participants = loadParticipants(trainingPlanId);
    TrainingExecutionLinkVO vo;
    if ("OFFLINE".equals(content.getContentType())) {
      vo = provisionOffline(plan, stage, content);
    } else if ("COURSE".equals(content.getContentType())) {
      vo = provisionCourse(plan, stage, content, participants);
    } else if ("EXAM".equals(content.getContentType()) || "QUESTION_BANK".equals(content.getContentType())) {
      vo = provisionAssessment(plan, stage, content, participants);
    } else {
      throw conflict(8143, "当前内容类型没有可关联的真实执行实例");
    }
    resultSyncService.ensurePending(trainingPlanId, content, vo.getExecutionId(),
                                    participantMapper.selectList(new LambdaQueryWrapper<TrainingParticipant>()
                                                                   .eq(TrainingParticipant::getTrainingPlanId,
                                                                       trainingPlanId)));
    return vo;
  }

  private TrainingExecutionLinkVO provisionCourse(TrainingPlan plan, TrainingPlanStage stage,
                                                  TrainingPlanContent content,
                                                  List<TrainingParticipantBinding> participants) {
    if (content.getResourceVersionId() == null) {
      throw conflict(8141, "课程内容没有已冻结的课程版本");
    }
    TrainingContentExecutionConfig config = configCodec.fromJson(content.getExecutionConfigJson());
    TrainingCourseOfferingCommand command = new TrainingCourseOfferingCommand();
    command.setOrganizationId(plan.getOrganizationId());
    command.setPlanContentId(content.getPlanContentId());
    command.setCourseId(content.getResourceId());
    command.setCourseVersionId(content.getResourceVersionId());
    command.setOfferingName(content.getContentName());
    command.setStartTime(firstTime(stage == null ? null : stage.getStartTime(), plan.getStartTime()));
    command.setEndTime(firstTime(stage == null ? null : stage.getEndTime(), plan.getEndTime()));
    command.setCompletionRule(config == null || config.getCompletionRule() == null
                                ? "ALL_REQUIRED_ITEMS" : config.getCompletionRule());
    command.setRequired(content.getIsRequired() != null && content.getIsRequired() == 1);
    Long offeringId = coursePort.ensureOffering(command);
    coursePort.publishOffering(offeringId);
    coursePort.ensureEnrollments(offeringId, content.getPlanContentId(), participants);
    return upsertLink(plan.getTrainingPlanId(), content, offeringId);
  }

  private TrainingExecutionLinkVO provisionAssessment(TrainingPlan plan, TrainingPlanStage stage,
                                                      TrainingPlanContent content,
                                                      List<TrainingParticipantBinding> participants) {
    TrainingContentExecutionConfig config = configCodec.fromJson(content.getExecutionConfigJson());
    TrainingExamExecutionCommand command = new TrainingExamExecutionCommand();
    command.setOrganizationId(plan.getOrganizationId());
    command.setPlanContentId(content.getPlanContentId());
    command.setContentType(content.getContentType());
    command.setExamName(content.getContentName());
    command.setOpenStartTime(firstTime(stage == null ? null : stage.getStartTime(), plan.getStartTime()));
    command.setOpenEndTime(firstTime(stage == null ? null : stage.getEndTime(), plan.getEndTime()));
    if ("EXAM".equals(content.getContentType())) {
      applyExamSnapshot(content, command);
    } else {
      TrainingQuestionPoolSnapshotService.FrozenPool frozenPool = questionPoolSnapshotService.load(content);
      command.setQuestionBankId(content.getResourceId());
      command.setAssessmentKind("BANK_PRACTICE");
      command.setResourceSnapshotHash(content.getResourceSnapshotHash());
      command.setQuestionPoolHash(frozenPool.poolHash());
      command.setQuestionPool(frozenPool.items());
      if (config != null) {
        command.setDurationSeconds(config.getDurationSeconds());
        command.setPassScore(config.getPassScore());
        command.setMaxAttempts(config.getMaxAttempts());
        command.setDrawRules(config.getDrawRules());
      }
    }
    Long assessmentExecutionId = assessmentPort.ensureExamExecution(command);
    assessmentPort.ensureCandidates(assessmentExecutionId, content.getPlanContentId(), participants);
    return upsertLink(plan.getTrainingPlanId(), content, assessmentExecutionId);
  }

  private TrainingExecutionLinkVO provisionOffline(TrainingPlan plan, TrainingPlanStage stage,
                                                   TrainingPlanContent content) {
    TrainingOfflineSession session = offlineSessionMapper.selectOne(
      new LambdaQueryWrapper<TrainingOfflineSession>()
        .eq(TrainingOfflineSession::getPlanContentId, content.getPlanContentId()));
    if (session == null) {
      TrainingContentExecutionConfig config = configCodec.fromJson(content.getExecutionConfigJson());
      session = new TrainingOfflineSession();
      session.setTrainingPlanId(plan.getTrainingPlanId());
      session.setPlanStageId(content.getPlanStageId());
      session.setPlanContentId(content.getPlanContentId());
      session.setSessionName(content.getContentName());
      session.setTeacherUserId(config == null ? null : config.getTeacherUserId());
      session.setLocation(firstText(config == null ? null : config.getLocation(),
                                    stage == null ? null : stage.getLocation(), plan.getLocation()));
      session.setStartTime(firstTime(config == null ? null : config.getStartTime(),
                                     stage == null ? null : stage.getStartTime(), plan.getStartTime()));
      session.setEndTime(firstTime(config == null ? null : config.getEndTime(),
                                   stage == null ? null : stage.getEndTime(), plan.getEndTime()));
      session.setSessionStatus("SCHEDULED");
      session.setStatus(1);
      try {
        offlineSessionMapper.insert(session);
      } catch (DuplicateKeyException ex) {
        TrainingOfflineSession concurrent = offlineSessionMapper.selectOne(
          new LambdaQueryWrapper<TrainingOfflineSession>()
            .eq(TrainingOfflineSession::getPlanContentId, content.getPlanContentId()));
        if (concurrent == null) {
          throw ex;
        }
        session = concurrent;
      }
    }
    TrainingExecutionLinkVO vo = new TrainingExecutionLinkVO();
    vo.setTrainingPlanId(plan.getTrainingPlanId());
    vo.setPlanContentId(content.getPlanContentId());
    vo.setExecutionType("OFFLINE");
    vo.setExecutionId(session.getOfflineSessionId());
    return vo;
  }

  private TrainingExecutionLinkVO upsertLink(Long trainingPlanId, TrainingPlanContent content, Long executionId) {
    TrainingExecutionLink existing = findLink(content.getPlanContentId(), content.getContentType());
    if (existing != null) {
      return toExecutionVO(existing);
    }
    TrainingExecutionLink link = new TrainingExecutionLink();
    link.setTrainingPlanId(trainingPlanId);
    link.setPlanContentId(content.getPlanContentId());
    link.setExecutionType(content.getContentType());
    link.setExecutionId(executionId);
    try {
      executionLinkMapper.insert(link);
    } catch (DuplicateKeyException ex) {
      TrainingExecutionLink concurrent = findLink(content.getPlanContentId(), content.getContentType());
      if (concurrent != null) {
        return toExecutionVO(concurrent);
      }
      throw ex;
    }
    return toExecutionVO(link);
  }

  private void applyExamSnapshot(TrainingPlanContent content, TrainingExamExecutionCommand command) {
    if (!StringUtils.hasText(content.getResourceSnapshotJson())
      || !StringUtils.hasText(content.getResourceSnapshotHash())) {
      throw conflict(8142, "计划内容 " + content.getPlanContentId() + " 缺少已校验的发布快照");
    }
    TrainingExamResourceSnapshot snapshot;
    try {
      snapshot = objectMapper.readValue(content.getResourceSnapshotJson(), TrainingExamResourceSnapshot.class);
    } catch (Exception ex) {
      throw conflict(8142, "计划内容 " + content.getPlanContentId() + " 发布快照无法解析");
    }
    if (snapshot != null) {
      snapshot.normalizeNumericFields();
    }
    if (!resourceSnapshotHashMatches(content.getResourceSnapshotJson(), content.getResourceSnapshotHash(), snapshot)) {
      throw conflict(8142, "计划内容 " + content.getPlanContentId() + " 发布快照无效");
    }
    if (snapshot == null || snapshot.getSchemaVersion() != 2
      || snapshot.getPaperVersionId() == null || snapshot.getDurationSeconds() == null
      || snapshot.getPassScore() == null || !StringUtils.hasText(snapshot.getQuestionOrderMode())
      || !StringUtils.hasText(snapshot.getMultiSelectScoring())
      || !StringUtils.hasText(snapshot.getAssessmentMode())
      || snapshot.getPolicy() == null) {
      throw conflict(8142, "计划内容 " + content.getPlanContentId() + " 发布快照缺少考试运行参数");
    }
    command.setSourceExamId(snapshot.getSourceExamId() == null ? content.getResourceId() : snapshot.getSourceExamId());
    command.setSourceExamCode(snapshot.getExamCode());
    command.setPaperVersionId(snapshot.getPaperVersionId());
    command.setAssessmentKind(snapshot.getAssessmentMode());
    command.setDurationSeconds(snapshot.getDurationSeconds());
    command.setPassScore(snapshot.getPassScore());
    command.setPassRatio(snapshot.getPassRatio());
    command.setQuestionOrderMode(snapshot.getQuestionOrderMode());
    command.setMultiSelectScoring(snapshot.getMultiSelectScoring());
    command.setCredit(snapshot.getCredit());
    command.setExamCategoryId(snapshot.getExamCategoryId());
    command.setCoverFileId(snapshot.getCoverFileId());
    command.setResponsibleUserId(snapshot.getResponsibleUserId());
    command.setIsNoticeEnabled(snapshot.getIsNoticeEnabled());
    command.setNoticeContent(snapshot.getNoticeContent());
    command.setPolicy(snapshot.getPolicy());
    command.setReviewers(snapshot.getReviewers());
    command.setResourceSnapshotHash(content.getResourceSnapshotHash());
    TrainingExamPolicySnapshot policy = snapshot.getPolicy();
    Integer retakeEnabled = policy.getIsRetakeEnabled();
    Integer retakeMax = policy.getRetakeMaxCount();
    int maxAttempts = Objects.equals(1, retakeEnabled) ? 1 + (retakeMax == null ? 0 : retakeMax) : 1;
    command.setMaxAttempts(maxAttempts);
    command.setAnswerDetailMode(policy.getAnswerDetailMode());
    command.setIsAnalysisVisible(policy.getIsAnalysisVisible());
    command.setIsScoreVisible(policy.getIsScoreVisible());
  }

  private boolean resourceSnapshotHashMatches(String json, String expectedHash,
                                              TrainingExamResourceSnapshot snapshot) {
    if (sha256Hex(json).equals(expectedHash)) {
      return true;
    }
    try {
      return sha256Hex(objectMapper.writeValueAsString(snapshot)).equals(expectedHash);
    } catch (Exception ex) {
      return false;
    }
  }

  private String sha256Hex(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder();
      for (byte b : hash) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (Exception ex) {
      throw conflict(8142, "发布快照哈希计算失败");
    }
  }

  private List<TrainingParticipantBinding> loadParticipants(Long trainingPlanId) {
    return participantMapper.selectList(new LambdaQueryWrapper<TrainingParticipant>()
                                          .eq(TrainingParticipant::getTrainingPlanId, trainingPlanId))
      .stream()
      .map(participant -> {
        TrainingParticipantBinding binding = new TrainingParticipantBinding();
        binding.setTrainingParticipantId(participant.getTrainingParticipantId());
        binding.setUserId(participant.getUserId());
        return binding;
      })
      .toList();
  }

  private TrainingExecutionLink findLink(Long contentId, String type) {
    return executionLinkMapper.selectOne(new LambdaQueryWrapper<TrainingExecutionLink>()
                                           .eq(TrainingExecutionLink::getPlanContentId, contentId)
                                           .eq(TrainingExecutionLink::getExecutionType, type));
  }

  private TrainingPlanContent requireContent(Long trainingPlanId, Long planContentId) {
    TrainingPlanContent content = contentMapper.selectById(planContentId);
    if (content == null) {
      throw notFound(8144, "培训计划内容不存在");
    }
    TrainingPlanStage stage = stageMapper.selectById(content.getPlanStageId());
    if (stage == null || !trainingPlanId.equals(stage.getTrainingPlanId())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 8145, "计划内容不属于指定培训计划");
    }
    return content;
  }

  private TrainingPlan requirePlan(Long id) {
    TrainingPlan plan = planMapper.lockForApproval(id, com.sts.admin.tenant.TenantContext.requireTenantId());
    if (plan == null) {
      throw notFound(8101, "培训计划不存在");
    }
    return plan;
  }

  private TrainingExecutionLinkVO toExecutionVO(TrainingExecutionLink link) {
    TrainingExecutionLinkVO vo = new TrainingExecutionLinkVO();
    BeanUtils.copyProperties(link, vo);
    return vo;
  }

  private static LocalDateTime firstTime(LocalDateTime... values) {
    if (values == null) {
      return null;
    }
    for (LocalDateTime value : values) {
      if (value != null) {
        return value;
      }
    }
    return null;
  }

  private static String firstText(String... values) {
    if (values == null) {
      return "";
    }
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return "";
  }

  private BusinessException notFound(int code, String message) {
    return new BusinessException(HttpStatus.NOT_FOUND, code, message);
  }

  private BusinessException conflict(int code, String message) {
    return new BusinessException(HttpStatus.CONFLICT, code, message);
  }
}
