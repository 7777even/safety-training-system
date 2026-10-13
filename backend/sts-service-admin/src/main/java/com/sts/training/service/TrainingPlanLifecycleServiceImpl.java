package com.sts.training.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sts.common.exception.BusinessException;
import com.sts.regulation.service.PlanHoursWarningRefreshService;
import com.sts.training.entity.*;
import com.sts.training.exception.TrainingPlanEndBlockedException;
import com.sts.training.mapper.*;
import com.sts.training.port.TrainingAssessmentExecutionPort;
import com.sts.training.port.TrainingCourseExecutionPort;
import com.sts.training.vo.TrainingPlanEndIssueVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 计划 start/end/cancel。结束前强制同步投影并执行门禁。
 */
@Service
@RequiredArgsConstructor
public class TrainingPlanLifecycleServiceImpl implements TrainingPlanLifecycleService {

  private static final List<String> TERMINAL = List.of("COMPLETED", "FAILED", "EXPIRED");

  private final TrainingPlanMapper planMapper;
  private final TrainingPlanStageMapper stageMapper;
  private final TrainingPlanContentMapper contentMapper;
  private final TrainingParticipantMapper participantMapper;
  private final TrainingExecutionLinkMapper executionLinkMapper;
  private final TrainingOfflineSessionMapper offlineSessionMapper;
  private final TrainingParticipantContentResultMapper resultMapper;
  private final TrainingCourseExecutionPort coursePort;
  private final TrainingAssessmentExecutionPort assessmentPort;
  private final TrainingResultSyncService resultSyncService;
  private final PlanHoursWarningRefreshService planHoursWarningRefreshService;

  @Override
  @Transactional
  public void start(Long trainingPlanId, Integer expectedVersion) {
    TrainingPlan plan = requirePlan(trainingPlanId);
    assertVersion(plan, expectedVersion);
    if (!"PUBLISHED".equals(plan.getPublishStatus())) {
      throw new BusinessException(HttpStatus.CONFLICT, 8161, "仅已发布计划可开始");
    }
    List<TrainingPlanEndIssueVO> missing = missingAllExecutions(plan);
    if (!missing.isEmpty()) {
      throw new BusinessException(HttpStatus.CONFLICT, 8162,
                                  "全部培训内容安排完成后才可开始：" + missing.getFirst().getPlanContentId());
    }
    plan.setPublishStatus("RUNNING");
    if (planMapper.updateById(plan) == 0) {
      throw new BusinessException(HttpStatus.CONFLICT, 8104, "培训计划版本冲突");
    }
  }

  @Override
  @Transactional
  public void end(Long trainingPlanId, Integer expectedVersion) {
    TrainingPlan plan = requirePlan(trainingPlanId);
    assertVersion(plan, expectedVersion);
    if (!"RUNNING".equals(plan.getPublishStatus())) {
      throw new BusinessException(HttpStatus.CONFLICT, 8163, "仅进行中的计划可结束");
    }
    List<TrainingPlanEndIssueVO> issues = new ArrayList<>(missingRequiredExecutions(plan));
    issues.addAll(inProgressIssues(plan));
    if (!issues.isEmpty()) {
      throw new TrainingPlanEndBlockedException(issues);
    }
    resultSyncService.synchronize(trainingPlanId);
    issues.addAll(incompleteResultIssues(plan));
    if (!issues.isEmpty()) {
      throw new TrainingPlanEndBlockedException(issues);
    }
    resultSyncService.freezePlan(trainingPlanId);
    plan.setPublishStatus("ENDED");
    plan.setEndedTime(LocalDateTime.now());
    if (planMapper.updateById(plan) == 0) {
      throw new BusinessException(HttpStatus.CONFLICT, 8104, "培训计划版本冲突");
    }
  }

  @Override
  @Transactional
  public void cancel(Long trainingPlanId, Integer expectedVersion) {
    TrainingPlan plan = requirePlan(trainingPlanId);
    assertVersion(plan, expectedVersion);
    if (!List.of("PUBLISHED", "RUNNING").contains(plan.getPublishStatus())) {
      throw new BusinessException(HttpStatus.CONFLICT, 8164, "仅已发布或进行中的计划可取消");
    }
    plan.setPublishStatus("CANCELLED");
    if (planMapper.updateById(plan) == 0) {
      throw new BusinessException(HttpStatus.CONFLICT, 8104, "培训计划版本冲突");
    }
    planHoursWarningRefreshService.refresh(trainingPlanId);
    for (TrainingParticipant participant : participantMapper.selectList(
      new LambdaQueryWrapper<TrainingParticipant>()
        .eq(TrainingParticipant::getTrainingPlanId, trainingPlanId))) {
      if ("PASSED".equals(participant.getResultStatus()) || participant.getResultStatus() == null) {
        participant.setResultStatus("INCOMPLETE");
        participantMapper.updateById(participant);
      }
    }
  }

  private List<TrainingPlanEndIssueVO> missingRequiredExecutions(TrainingPlan plan) {
    List<TrainingPlanEndIssueVO> issues = new ArrayList<>();
    for (TrainingPlanContent content : requiredContents(plan.getTrainingPlanId())) {
      if (!hasExecution(content)) {
        issues.add(issue(null, content.getPlanContentId(), "MISSING_EXECUTION", "必修内容尚未生成真实执行"));
      }
    }
    return issues;
  }

  /** 开始培训前必须为计划中的每项内容建立真实执行安排。 */
  private List<TrainingPlanEndIssueVO> missingAllExecutions(TrainingPlan plan) {
    List<TrainingPlanEndIssueVO> issues = new ArrayList<>();
    for (TrainingPlanContent content : contents(plan.getTrainingPlanId())) {
      if (!hasExecution(content)) {
        issues.add(issue(null, content.getPlanContentId(), "MISSING_EXECUTION", "培训内容尚未完成安排"));
      }
    }
    return issues;
  }

  private List<TrainingPlanEndIssueVO> inProgressIssues(TrainingPlan plan) {
    List<TrainingPlanEndIssueVO> issues = new ArrayList<>();
    for (TrainingPlanContent content : contents(plan.getTrainingPlanId())) {
      if ("COURSE".equals(content.getContentType())) {
        TrainingExecutionLink link = findLink(content.getPlanContentId(), "COURSE");
        if (link == null) {
          continue;
        }
        for (Long participantId : coursePort.listInProgressLearningParticipantIds(link.getExecutionId())) {
          issues.add(issue(participantId, content.getPlanContentId(), "IN_PROGRESS_LEARNING",
                           "存在进行中的课程学习"));
        }
      } else if ("EXAM".equals(content.getContentType()) || "QUESTION_BANK".equals(content.getContentType())) {
        TrainingExecutionLink link = findLink(content.getPlanContentId(), content.getContentType());
        if (link == null) {
          continue;
        }
        for (Long participantId : assessmentPort.listInProgressAttemptParticipantIds(link.getExecutionId())) {
          issues.add(issue(participantId, content.getPlanContentId(), "IN_PROGRESS_ATTEMPT",
                           "存在进行中的考试作答"));
        }
      } else if ("OFFLINE".equals(content.getContentType())) {
        TrainingOfflineSession session = offlineSessionMapper.selectOne(
          new LambdaQueryWrapper<TrainingOfflineSession>()
            .eq(TrainingOfflineSession::getPlanContentId, content.getPlanContentId())
            .last("LIMIT 1"));
        if (session != null && "IN_PROGRESS".equals(session.getSessionStatus())) {
          issues.add(issue(null, content.getPlanContentId(), "IN_PROGRESS_SESSION",
                           "线下场次仍在进行中"));
        }
      }
    }
    return issues;
  }

  private List<TrainingPlanEndIssueVO> incompleteResultIssues(TrainingPlan plan) {
    List<TrainingPlanEndIssueVO> issues = new ArrayList<>();
    List<TrainingPlanContent> required = requiredContents(plan.getTrainingPlanId());
    List<TrainingParticipant> participants = participantMapper.selectList(
      new LambdaQueryWrapper<TrainingParticipant>()
        .eq(TrainingParticipant::getTrainingPlanId, plan.getTrainingPlanId()));
    for (TrainingParticipant participant : participants) {
      for (TrainingPlanContent content : required) {
        TrainingParticipantContentResult row = resultMapper.selectOne(
          new LambdaQueryWrapper<TrainingParticipantContentResult>()
            .eq(TrainingParticipantContentResult::getTrainingParticipantId,
                participant.getTrainingParticipantId())
            .eq(TrainingParticipantContentResult::getPlanContentId, content.getPlanContentId()));
        if (row == null) {
          issues.add(issue(participant.getTrainingParticipantId(), content.getPlanContentId(),
                           "MISSING_PROJECTION", "缺少逐内容结果投影"));
        } else if (!TERMINAL.contains(row.getResultStatus())) {
          issues.add(issue(participant.getTrainingParticipantId(), content.getPlanContentId(),
                           "RESULT_NOT_FINAL", "必修内容没有完成、失败或到期结论"));
        }
      }
    }
    return issues;
  }

  private boolean hasExecution(TrainingPlanContent content) {
    if ("OFFLINE".equals(content.getContentType())) {
      return offlineSessionMapper.selectCount(new LambdaQueryWrapper<TrainingOfflineSession>()
                                                .eq(TrainingOfflineSession::getPlanContentId,
                                                    content.getPlanContentId())) > 0;
    }
    return findLink(content.getPlanContentId(), content.getContentType()) != null;
  }

  private TrainingExecutionLink findLink(Long contentId, String type) {
    return executionLinkMapper.selectOne(new LambdaQueryWrapper<TrainingExecutionLink>()
                                           .eq(TrainingExecutionLink::getPlanContentId, contentId)
                                           .eq(TrainingExecutionLink::getExecutionType, type));
  }

  private List<TrainingPlanContent> requiredContents(Long trainingPlanId) {
    return contents(trainingPlanId).stream()
      .filter(content -> content.getIsRequired() != null && content.getIsRequired() == 1)
      .toList();
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

  private void assertVersion(TrainingPlan plan, Integer expectedVersion) {
    if (expectedVersion == null) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 8104, "培训计划版本冲突");
    }
    plan.setVersion(expectedVersion);
  }

  private TrainingPlan requirePlan(Long id) {
    TrainingPlan plan = planMapper.lockForApproval(id, com.sts.admin.tenant.TenantContext.requireTenantId());
    if (plan == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 8101, "培训计划不存在");
    }
    return plan;
  }

  private TrainingPlanEndIssueVO issue(Long participantId, Long contentId, String reason, String message) {
    TrainingPlanEndIssueVO vo = new TrainingPlanEndIssueVO();
    vo.setTrainingParticipantId(participantId);
    vo.setPlanContentId(contentId);
    vo.setReasonCode(reason);
    vo.setMessage(message);
    return vo;
  }
}
