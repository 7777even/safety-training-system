package com.sts.training.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sts.admin.entity.SysUser;
import com.sts.admin.mapper.SysFileInfoMapper;
import com.sts.admin.mapper.SysUserMapper;
import com.sts.admin.storage.StorageService;
import com.sts.common.exception.BusinessException;
import com.sts.exam.vo.ExamAttemptVO;
import com.sts.training.dto.TrainingExamAttemptStartRequest;
import com.sts.training.dto.TrainingLearnerEnterRequest;
import com.sts.training.entity.*;
import com.sts.training.mapper.*;
import com.sts.training.port.TrainingAssessmentExecutionPort;
import com.sts.training.port.TrainingCourseExecutionPort;
import com.sts.training.port.TrainingCourseResult;
import com.sts.training.vo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TrainingWorkflowServiceImpl implements TrainingWorkflowService {

  /** 已发布仅供管理员完成内容安排，学员只在执行中或结束后看到计划。 */
  private static final List<String> LEARNER_VISIBLE_PLAN_STATUSES = List.of("RUNNING", "ENDED");

  private final TrainingPlanMapper planMapper;
  private final TrainingParticipantMapper participantMapper;
  private final TrainingPlanStageMapper stageMapper;
  private final TrainingPlanContentMapper contentMapper;
  private final TrainingExecutionLinkMapper executionLinkMapper;
  private final TrainingOfflineSessionMapper offlineSessionMapper;
  private final TrainingOfflineScoreMapper offlineScoreMapper;
  private final TrainingRewardPenaltyMapper rewardPenaltyMapper;
  private final TrainingCourseExecutionPort coursePort;
  private final TrainingAssessmentExecutionPort assessmentPort;
  private final TrainingExecutionService trainingExecutionService;
  private final TrainingResultSyncService resultSyncService;
  private final TrainingTypeMapper trainingTypeMapper;
  private final SysFileInfoMapper fileInfoMapper;
  private final StorageService storageService;
  private final SysUserMapper userMapper;
  private final TrainingAttachmentMapper attachmentMapper;
  /** 学员侧只读资料复用资料服务的受控读取，避免另写一套取文件逻辑。 */
  private final TrainingAttachmentService attachmentService;
  /** 名单移除同样受审批保护。 */ private final TrainingPlanApprovalService approvalService;

  @Override
  @Transactional
  public void removeParticipant(Long trainingPlanId, Long trainingParticipantId) {
    TrainingPlan plan = approvalService.lockForEdit(trainingPlanId);
    if (!"DRAFT".equals(plan.getPublishStatus())) {
      throw conflict(8119, "仅草稿计划可移除参训人");
    }
    TrainingParticipant participant = requireParticipant(trainingParticipantId);
    if (!trainingPlanId.equals(participant.getTrainingPlanId())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 8120, "参训人不属于指定计划");
    }
    if (hasDownstreamFacts(participant)) {
      throw conflict(8122, "参训人已有执行或结果事实，不可移除");
    }
    participantMapper.update(null, Wrappers.<TrainingParticipant>update()
      .set("deleted", trainingParticipantId)
      .eq("training_participant_id", trainingParticipantId)
      .eq("deleted", 0L));
    if (planMapper.updateById(plan) == 0) throw conflict(8104, "培训计划版本冲突");
  }

  @Override
  public TrainingTeacherTaskDetailVO getTeacherTask(Long offlineSessionId, Long teacherUserId) {
    TrainingOfflineSession session = offlineSessionMapper.selectById(offlineSessionId);
    if (session == null) throw notFound(8130, "线下场次不存在");
    if (!Objects.equals(session.getTeacherUserId(), teacherUserId)) {
      throw new BusinessException(HttpStatus.FORBIDDEN, 8131, "当前用户不是该场次教师");
    }
    TrainingTeacherTaskDetailVO vo = new TrainingTeacherTaskDetailVO();
    vo.setOfflineSessionId(session.getOfflineSessionId());
    vo.setTrainingPlanId(session.getTrainingPlanId());
    vo.setSessionName(session.getSessionName());
    vo.setLocation(session.getLocation());
    vo.setStartTime(session.getStartTime());
    vo.setEndTime(session.getEndTime());
    vo.setSessionStatus(session.getSessionStatus());
    vo.setVersion(session.getVersion());
    List<TrainingParticipant> participants = participantMapper.selectList(
      new LambdaQueryWrapper<TrainingParticipant>()
        .eq(TrainingParticipant::getTrainingPlanId, session.getTrainingPlanId()));
    List<TrainingOfflineScore> scores = offlineScoreMapper.selectList(
      new LambdaQueryWrapper<TrainingOfflineScore>()
        .eq(TrainingOfflineScore::getOfflineSessionId, offlineSessionId)
        .isNotNull(TrainingOfflineScore::getScoredBy));
    Map<Long, TrainingOfflineScore> scoreByParticipant = new HashMap<>();
    for (TrainingOfflineScore score : scores) {
      TrainingOfflineScore current = scoreByParticipant.get(score.getTrainingParticipantId());
      if (current == null || (score.getScoredTime() != null
        && (current.getScoredTime() == null || score.getScoredTime().isAfter(current.getScoredTime())))) {
        scoreByParticipant.put(score.getTrainingParticipantId(), score);
      }
    }
    List<TrainingTeacherRosterItemVO> roster = new ArrayList<>();
    List<Long> userIds = participants.stream().map(TrainingParticipant::getUserId)
      .filter(Objects::nonNull).distinct().toList();
    Map<Long, String> userNameById = new HashMap<>();
    if (!userIds.isEmpty()) {
      userMapper.selectList(new LambdaQueryWrapper<SysUser>().in(SysUser::getUserId, userIds))
        .forEach(user -> {
          if (user.getUserName() != null && !user.getUserName().isBlank()) {
            userNameById.put(user.getUserId(), user.getUserName());
          }
        });
    }
    for (TrainingParticipant participant : participants) {
      TrainingTeacherRosterItemVO item = new TrainingTeacherRosterItemVO();
      item.setTrainingParticipantId(participant.getTrainingParticipantId());
      item.setUserId(participant.getUserId());
      item.setUserName(userNameById.get(participant.getUserId()));
      TrainingOfflineScore score = scoreByParticipant.get(participant.getTrainingParticipantId());
      if (score != null) {
        item.setScore(score.getScore());
        item.setMaxScore(score.getMaxScore());
        item.setScoredTime(score.getScoredTime());
      }
      TrainingParticipantResultsVO results = resultSyncService.getResults(
        session.getTrainingPlanId(), participant.getTrainingParticipantId());
      if (results != null && results.getItems() != null) {
        results.getItems().stream()
          .filter(row -> session.getPlanContentId().equals(row.getPlanContentId()))
          .findFirst()
          .ifPresent(row -> item.setResultStatus(row.getResultStatus()));
      }
      roster.add(item);
    }
    vo.setRoster(roster);
    vo.setParticipantCount(participants.size());
    vo.setScoredCount(roster.stream().filter(item -> item.getScore() != null).count());
    vo.setPendingScoreCount(Math.max(0, vo.getParticipantCount() - vo.getScoredCount()));
    List<String> actions = new ArrayList<>();
    if ("SCHEDULED".equals(session.getSessionStatus())) {
      actions.add("START");
      actions.add("CANCEL");
    } else if ("IN_PROGRESS".equals(session.getSessionStatus())) {
      actions.add("RECORD_SCORE");
      actions.add("COMPLETE");
      actions.add("CANCEL");
    }
    vo.setAvailableActions(actions);
    return vo;
  }

  @Override
  public List<TrainingExecutionLinkVO> listExecutions(Long trainingPlanId) {
    requirePlan(trainingPlanId);
    List<TrainingExecutionLinkVO> result = executionLinkMapper.selectList(
        new LambdaQueryWrapper<TrainingExecutionLink>()
          .eq(TrainingExecutionLink::getTrainingPlanId, trainingPlanId)
          .orderByAsc(TrainingExecutionLink::getPlanContentId))
      .stream().map(this::toExecutionVO).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    for (TrainingOfflineSession session : offlineSessionMapper.selectList(
      new LambdaQueryWrapper<TrainingOfflineSession>()
        .eq(TrainingOfflineSession::getTrainingPlanId, trainingPlanId))) {
      TrainingExecutionLinkVO vo = new TrainingExecutionLinkVO();
      vo.setTrainingPlanId(trainingPlanId);
      vo.setPlanContentId(session.getPlanContentId());
      vo.setExecutionType("OFFLINE");
      vo.setExecutionId(session.getOfflineSessionId());
      result.add(vo);
    }
    result.sort(Comparator.comparing(TrainingExecutionLinkVO::getPlanContentId,
                                     Comparator.nullsLast(Comparator.naturalOrder())));
    return result;
  }

  @Override
  public TrainingExecutionLinkVO provision(Long trainingPlanId, Long planContentId) {
    return trainingExecutionService.provision(trainingPlanId, planContentId);
  }

  @Override
  @Transactional
  public ExamAttemptVO startExam(Long trainingParticipantId, Long planContentId,
                                 TrainingExamAttemptStartRequest request, Long currentUserId) {
    TrainingLearnerEnterRequest enter = new TrainingLearnerEnterRequest();
    enter.setClientAttemptToken(request.getClientAttemptToken());
    enter.setPlatform(request.getPlatform());
    enter.setOperatingSystem(request.getOperatingSystem());
    enter.setBrowser(request.getBrowser());
    enter.setClientIp(request.getClientIp());
    TrainingLearnerEnterVO vo = enter(trainingParticipantId, planContentId, enter, currentUserId);
    if (vo.getExamAttemptId() == null) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 8151, "计划内容不是考试或题库练习");
    }
    ExamAttemptVO attempt = new ExamAttemptVO();
    attempt.setExamAttemptId(vo.getExamAttemptId());
    return attempt;
  }

  @Override
  public TrainingLearnerExamNoticeVO getLearnerExamNotice(Long trainingParticipantId, Long planContentId,
                                                            Long currentUserId) {
    TrainingParticipant participant = requireParticipant(trainingParticipantId);
    if (!Objects.equals(participant.getUserId(), currentUserId)) {
      throw new BusinessException(HttpStatus.FORBIDDEN, 8150, "只能查看当前登录学员的培训考试须知");
    }
    TrainingPlan plan = requirePlan(participant.getTrainingPlanId());
    if (!"RUNNING".equals(plan.getPublishStatus())) {
      throw conflict(8154, "仅进行中的计划可查看考试须知");
    }
    TrainingPlanContent content = requireContent(participant.getTrainingPlanId(), planContentId);
    if (!"EXAM".equals(content.getContentType())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 8151, "计划内容不是理论考试");
    }
    TrainingExecutionLink link = findLink(planContentId, "EXAM");
    if (link == null) {
      throw conflict(8152, "考试执行实例尚未补建");
    }
    return assessmentPort.getLearnerExamNotice(link.getExecutionId());
  }

  @Override
  @Transactional
  public TrainingLearnerEnterVO enter(Long trainingParticipantId, Long planContentId,
                                      TrainingLearnerEnterRequest request, Long currentUserId) {
    TrainingParticipant participant = requireParticipant(trainingParticipantId);
    if (!Objects.equals(participant.getUserId(), currentUserId)) {
      throw new BusinessException(HttpStatus.FORBIDDEN, 8150, "只能进入当前登录学员的培训内容");
    }
    TrainingPlan plan = requirePlan(participant.getTrainingPlanId());
    if (!"RUNNING".equals(plan.getPublishStatus())) {
      throw conflict(8154, "仅进行中的计划可进入");
    }
    TrainingPlanContent content = requireContent(participant.getTrainingPlanId(), planContentId);
    TrainingLearnerEnterVO vo = new TrainingLearnerEnterVO();
    vo.setContentType(content.getContentType());
    if ("COURSE".equals(content.getContentType())) {
      TrainingExecutionLink link = findLink(planContentId, "COURSE");
      if (link == null) {
        throw conflict(8152, "课程执行实例尚未补建");
      }
      TrainingCourseResult enrollment = coursePort.readResult(link.getExecutionId(), trainingParticipantId);
      if (enrollment == null || enrollment.getSourceRecordId() == null) {
        throw conflict(8152, "课程执行实例尚未补建");
      }
      vo.setCourseEnrollmentId(enrollment.getSourceRecordId());
      return vo;
    }
    if ("EXAM".equals(content.getContentType()) || "QUESTION_BANK".equals(content.getContentType())) {
      TrainingExecutionLink link = findLink(planContentId, content.getContentType());
      if (link == null) {
        throw conflict(8152, "考试执行实例尚未补建");
      }
      String token = request == null ? null : request.getClientAttemptToken();
      if (token == null || token.isBlank()) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, 8153, "考试进入必须提供客户端作答令牌");
      }
      ExamAttemptVO attempt = assessmentPort.startAttempt(
        link.getExecutionId(), trainingParticipantId, currentUserId, token,
        request.getPlatform(), request.getOperatingSystem(),
        request.getBrowser(), request.getClientIp());
      vo.setExamAttemptId(attempt.getExamAttemptId());
      return vo;
    }
    if ("OFFLINE".equals(content.getContentType())) {
      TrainingOfflineSession session = offlineSessionMapper.selectOne(
        new LambdaQueryWrapper<TrainingOfflineSession>()
          .eq(TrainingOfflineSession::getPlanContentId, planContentId)
          .last("LIMIT 1"));
      if (session == null) {
        throw conflict(8152, "线下场次尚未补建");
      }
      vo.setOfflineSessionId(session.getOfflineSessionId());
      vo.setSessionName(session.getSessionName());
      vo.setLocation(session.getLocation());
      vo.setStartTime(session.getStartTime());
      vo.setEndTime(session.getEndTime());
      return vo;
    }
    throw new BusinessException(HttpStatus.BAD_REQUEST, 8151, "不支持的培训内容类型");
  }

  @Override
  public List<TrainingLearnerTaskVO> listLearnerTasks(Long currentUserId) {
    List<TrainingLearnerTaskVO> tasks = participantMapper.selectList(new LambdaQueryWrapper<TrainingParticipant>()
                                          .eq(TrainingParticipant::getUserId, currentUserId)
                                          .orderByDesc(TrainingParticipant::getJoinedTime))
      .stream().map(participant -> {
        TrainingPlan plan = requirePlan(participant.getTrainingPlanId());
        if (!isLearnerVisiblePlan(plan)) {
          return null;
        }
        TrainingLearnerTaskVO vo = new TrainingLearnerTaskVO();
        vo.setTrainingParticipantId(participant.getTrainingParticipantId());
        vo.setTrainingPlanId(plan.getTrainingPlanId());
        vo.setTrainingTypeId(plan.getTrainingTypeId());
        vo.setPlanName(plan.getPlanName());
        vo.setCoverUrl(learnerPlanCoverUrl(plan));
        vo.setStartTime(plan.getStartTime());
        vo.setEndTime(plan.getEndTime());
        vo.setPublishStatus(plan.getPublishStatus());
        vo.setParticipantStatus(participant.getParticipantStatus());
        // 执行中的参训人尚未结束冻结，列表必须读取实时投影结果，不能回传尚未落库的空状态。
        TrainingParticipantResultsVO results = resultSyncService.getResults(
          plan.getTrainingPlanId(), participant.getTrainingParticipantId());
        vo.setResultStatus(results == null ? participant.getResultStatus() : results.getResultStatus());
        return vo;
      }).filter(Objects::nonNull).toList();
    fillTrainingTypeNames(tasks);
    return tasks;
  }

  /** 按本批任务的 trainingTypeId 集合一次性查名称回填，避免逐条类型查询。 */
  private void fillTrainingTypeNames(List<TrainingLearnerTaskVO> tasks) {
    List<Long> typeIds = tasks.stream()
      .map(TrainingLearnerTaskVO::getTrainingTypeId)
      .filter(Objects::nonNull)
      .distinct()
      .toList();
    if (typeIds.isEmpty()) {
      return;
    }
    Map<Long, String> namesById = trainingTypeMapper.selectBatchIds(typeIds).stream()
      .filter(type -> type != null && type.getTypeName() != null)
      .collect(Collectors.toMap(TrainingType::getTrainingTypeId, TrainingType::getTypeName, (a, b) -> a));
    tasks.forEach(vo -> vo.setTrainingTypeName(namesById.get(vo.getTrainingTypeId())));
  }

  @Override
  public TrainingCoverFile openLearnerPlanCover(Long trainingPlanId, Long currentUserId) {
    TrainingPlan plan = planMapper.selectById(trainingPlanId);
    if (plan == null || !isLearnerVisiblePlan(plan)) {
      throw notFound(8155, "培训任务对当前学员不可见");
    }
    TrainingParticipant participant = participantMapper.selectOne(
      new LambdaQueryWrapper<TrainingParticipant>()
        .eq(TrainingParticipant::getTrainingPlanId, trainingPlanId)
        .eq(TrainingParticipant::getUserId, currentUserId));
    if (participant == null) {
      throw notFound(8155, "培训任务对当前学员不可见");
    }
    return TrainingCoverSupport.open(fileInfoMapper, storageService, typeCoverFileId(plan));
  }

  @Override
  public TrainingLearnerParticipantDetailVO getLearnerParticipant(Long trainingParticipantId,
                                                                  Long currentUserId) {
    TrainingParticipant participant = requireParticipant(trainingParticipantId);
    if (!Objects.equals(participant.getUserId(), currentUserId)) {
      throw new BusinessException(HttpStatus.FORBIDDEN, 8150, "只能查看当前登录学员的培训任务");
    }
    TrainingPlan plan = requirePlan(participant.getTrainingPlanId());
    requireLearnerVisiblePlan(plan);
    TrainingLearnerParticipantDetailVO detail = new TrainingLearnerParticipantDetailVO();
    detail.setParticipant(toParticipantVO(participant));
    TrainingPlanVO planVO = new TrainingPlanVO();
    BeanUtils.copyProperties(plan, planVO);
    detail.setPlan(planVO);
    detail.setContents(listContents(plan.getTrainingPlanId()));
    detail.setResults(resultSyncService.getResults(plan.getTrainingPlanId(), trainingParticipantId).getItems());
    return detail;
  }

  @Override
  public TrainingLearnerOfflineSessionVO getLearnerOfflineSession(Long offlineSessionId, Long currentUserId) {
    TrainingOfflineSession session = offlineSessionMapper.selectById(offlineSessionId);
    if (session == null) {
      throw notVisible();
    }
    TrainingPlan plan = planMapper.selectById(session.getTrainingPlanId());
    if (plan == null || !isLearnerVisiblePlan(plan)) {
      throw notVisible();
    }
    TrainingParticipant participant = participantMapper.selectOne(
      new LambdaQueryWrapper<TrainingParticipant>()
        .eq(TrainingParticipant::getTrainingPlanId, plan.getTrainingPlanId())
        .eq(TrainingParticipant::getUserId, currentUserId));
    if (participant == null) {
      throw notVisible();
    }
    TrainingOfflineScore score = offlineScoreMapper.selectOne(
      new LambdaQueryWrapper<TrainingOfflineScore>()
        .eq(TrainingOfflineScore::getOfflineSessionId, offlineSessionId)
        .eq(TrainingOfflineScore::getTrainingParticipantId, participant.getTrainingParticipantId())
        .isNotNull(TrainingOfflineScore::getScoredBy)
        .orderByDesc(TrainingOfflineScore::getScoredTime)
        .last("LIMIT 1"));
    TrainingLearnerOfflineSessionVO vo = new TrainingLearnerOfflineSessionVO();
    vo.setOfflineSessionId(session.getOfflineSessionId());
    vo.setTrainingPlanId(plan.getTrainingPlanId());
    vo.setTrainingParticipantId(participant.getTrainingParticipantId());
    vo.setPlanContentId(session.getPlanContentId());
    vo.setSessionName(session.getSessionName());
    vo.setLocation(session.getLocation());
    vo.setStartTime(session.getStartTime());
    vo.setEndTime(session.getEndTime());
    vo.setSessionStatus(session.getSessionStatus());
    vo.setResultStatus("PENDING");
    vo.setEffectiveDurationSeconds(0);
    if (score != null) {
      vo.setScore(score.getScore());
      vo.setScoredTime(score.getScoredTime());
    }
    TrainingParticipantResultsVO results = resultSyncService.getResults(
      plan.getTrainingPlanId(), participant.getTrainingParticipantId());
    if (results != null && results.getItems() != null) {
      results.getItems().stream()
        .filter(item -> session.getPlanContentId().equals(item.getPlanContentId()))
        .findFirst()
        .ifPresent(item -> {
          vo.setResultStatus(item.getResultStatus() == null ? "PENDING" : item.getResultStatus());
          vo.setEffectiveDurationSeconds(
            item.getEffectiveDurationSeconds() == null ? 0 : item.getEffectiveDurationSeconds());
        });
    }
    return vo;
  }

  @Override
  public List<TrainingLearnerMaterialVO> listLearnerMaterials(Long trainingParticipantId, Long currentUserId) {
    TrainingPlan plan = requireLearnerPlan(trainingParticipantId, currentUserId);
    Map<Long, String> stageNames = learnerStageNames(plan.getTrainingPlanId());
    return learnerMaterials(plan.getTrainingPlanId()).stream()
      .map(attachment -> toLearnerMaterialVO(attachment, stageNames))
      .toList();
  }

  @Override
  public TrainingAttachmentFile openLearnerMaterialFile(Long trainingParticipantId, Long trainingAttachmentId,
                                                        Long currentUserId) {
    TrainingPlan plan = requireLearnerPlan(trainingParticipantId, currentUserId);
    TrainingAttachment attachment = attachmentMapper.selectById(trainingAttachmentId);
    // 只放行「本计划 + 一般资料 + 启用」三者同时成立的记录；其余一律按不可读处理，不泄漏存在性。
    if (attachment == null
      || !Objects.equals(attachment.getTrainingPlanId(), plan.getTrainingPlanId())
      || !"GENERAL".equals(attachment.getAttachmentType())
      || !Integer.valueOf(1).equals(attachment.getStatus())) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 8233, "培训资料文件不存在或不可读取");
    }
    return attachmentService.openFile(trainingAttachmentId);
  }

  /**
   * 学员侧资料可见性收口：参训记录必须存在、必须属于当前登录学员、且计划对学员可见。
   *
   * <p>三条不满足统一返回 8155/404（**不复用 {@code requireParticipant} 的 8110**），
   * 否则调用方可以借业务码区分「参训记录不存在」与「参训记录属于他人」，等于泄漏存在性。
   */
  private TrainingPlan requireLearnerPlan(Long trainingParticipantId, Long currentUserId) {
    TrainingParticipant participant = participantMapper.selectById(trainingParticipantId);
    if (participant == null || !Objects.equals(participant.getUserId(), currentUserId)) {
      throw notFound(8155, "培训任务对当前学员不可见");
    }
    TrainingPlan plan = requirePlan(participant.getTrainingPlanId());
    requireLearnerVisiblePlan(plan);
    return plan;
  }

  /** 学员侧只取一般资料且只取启用态；停用资料与影像/评价佐证一律不下发。 */
  private List<TrainingAttachment> learnerMaterials(Long trainingPlanId) {
    return attachmentMapper.selectList(new LambdaQueryWrapper<TrainingAttachment>()
      .eq(TrainingAttachment::getTrainingPlanId, trainingPlanId)
      .eq(TrainingAttachment::getAttachmentType, "GENERAL")
      .eq(TrainingAttachment::getStatus, 1)
      .orderByAsc(TrainingAttachment::getSortOrder)
      .orderByAsc(TrainingAttachment::getTrainingAttachmentId));
  }

  private Map<Long, String> learnerStageNames(Long trainingPlanId) {
    return stageMapper.selectList(new LambdaQueryWrapper<TrainingPlanStage>()
        .eq(TrainingPlanStage::getTrainingPlanId, trainingPlanId)).stream()
      .filter(stage -> stage.getPlanStageId() != null)
      .collect(java.util.stream.Collectors.toMap(TrainingPlanStage::getPlanStageId,
        stage -> Objects.toString(stage.getStageName(), ""), (first, ignored) -> first));
  }

  private static TrainingLearnerMaterialVO toLearnerMaterialVO(TrainingAttachment attachment,
                                                               Map<Long, String> stageNames) {
    TrainingLearnerMaterialVO vo = new TrainingLearnerMaterialVO();
    vo.setTrainingAttachmentId(attachment.getTrainingAttachmentId());
    vo.setAttachmentName(attachment.getAttachmentName());
    vo.setDescription(attachment.getDescription());
    vo.setPlanStageId(attachment.getPlanStageId());
    vo.setStageName(attachment.getPlanStageId() == null ? null : stageNames.get(attachment.getPlanStageId()));
    vo.setSortOrder(attachment.getSortOrder());
    vo.setHasFile(attachment.getFileInfoId() != null);
    return vo;
  }

  @Override
  @Transactional
  public List<TrainingParticipantResultsVO> synchronizeResults(Long trainingPlanId) {
    reconcileTrainingCourseScores(trainingPlanId);
    return resultSyncService.synchronize(trainingPlanId);
  }

  /**
   * 全量补账前仅修复未冻结参训人的培训课程空分，避免改写已冻结历史或独立课程。
   */
  private void reconcileTrainingCourseScores(Long trainingPlanId) {
    List<TrainingParticipant> participants = participantMapper.selectList(
      new LambdaQueryWrapper<TrainingParticipant>()
        .eq(TrainingParticipant::getTrainingPlanId, trainingPlanId)
        .isNull(TrainingParticipant::getResultFinalizedTime));
    if (participants.isEmpty()) {
      return;
    }
    for (TrainingPlanContent content : contents(trainingPlanId)) {
      if (!"COURSE".equals(content.getContentType())) {
        continue;
      }
      Long courseOfferingId = coursePort.findOfferingIdByPlanContentId(content.getPlanContentId());
      if (courseOfferingId == null) {
        continue;
      }
      participants.forEach(participant -> coursePort.reconcileTrainingFinalScore(
        courseOfferingId, participant.getTrainingParticipantId()));
    }
  }

  @Override
  public TrainingParticipantResultsVO getResults(Long trainingPlanId, Long trainingParticipantId) {
    return resultSyncService.getResults(trainingPlanId, trainingParticipantId);
  }

  @Override
  public TrainingEndedResultVO getEndedResult(Long trainingPlanId) {
    TrainingPlan plan = requirePlan(trainingPlanId);
    if (!"ENDED".equals(plan.getPublishStatus())) {
      throw conflict(8123, "仅已结束培训可查看正式结果");
    }
    List<TrainingParticipant> participants = participantMapper.selectList(
      new LambdaQueryWrapper<TrainingParticipant>().eq(TrainingParticipant::getTrainingPlanId, trainingPlanId));
    List<TrainingParticipantResultsVO> results = new ArrayList<>();
    long passed = 0;
    long failed = 0;
    long incomplete = 0;
    BigDecimal scoreSum = BigDecimal.ZERO;
    int scoredCount = 0;
    for (TrainingParticipant participant : participants) {
      TrainingParticipantResultsVO row = resultSyncService.getResults(trainingPlanId,
                                                                      participant.getTrainingParticipantId());
      if (row.getUserId() == null) {
        row.setUserId(participant.getUserId());
      }
      results.add(row);
      String status = participant.getResultStatus();
      if ("INCOMPLETE".equals(status)) {
        incomplete++;
      } else if ("PASSED".equals(status)) {
        passed++;
      } else {
        failed++;
      }
      if (participant.getCompositeScore() != null) {
        scoreSum = scoreSum.add(participant.getCompositeScore());
        scoredCount++;
      }
    }
    List<TrainingRewardPenaltyVO> rewards = rewardPenaltyMapper.selectList(
        new LambdaQueryWrapper<TrainingRewardPenalty>()
          .eq(TrainingRewardPenalty::getTrainingPlanId, trainingPlanId)
          .eq(TrainingRewardPenalty::getStatus, 1)
          .orderByDesc(TrainingRewardPenalty::getCreateTime))
      .stream().map(this::toRewardVO).toList();
    TrainingEndedResultVO vo = new TrainingEndedResultVO();
    vo.setTrainingPlanId(plan.getTrainingPlanId());
    vo.setPlanCode(plan.getPlanCode());
    vo.setPlanName(plan.getPlanName());
    vo.setPublishStatus(plan.getPublishStatus());
    vo.setParticipantCount((long) participants.size());
    vo.setPassedCount(passed);
    vo.setFailedCount(failed);
    vo.setIncompleteCount(incomplete);
    vo.setParticipants(results);
    vo.setRewardPenalties(rewards);
    vo.setAverageCompositeScore(scoredCount == 0
                                  ? null : scoreSum.divide(BigDecimal.valueOf(scoredCount), 2, RoundingMode.HALF_UP));
    return vo;
  }

  private boolean hasDownstreamFacts(TrainingParticipant participant) {
    Long id = participant.getTrainingParticipantId();
    return offlineScoreMapper.selectCount(new LambdaQueryWrapper<TrainingOfflineScore>()
                                          .eq(TrainingOfflineScore::getTrainingParticipantId, id)) > 0
      || rewardPenaltyMapper.selectCount(new LambdaQueryWrapper<TrainingRewardPenalty>()
                                           .eq(TrainingRewardPenalty::getTrainingParticipantId, id)) > 0
      || coursePort.existsEnrollmentForParticipant(id)
      || assessmentPort.existsCandidateForParticipant(id)
      || participant.getOfficialCalculationRunId() != null
      || participant.getResultStatus() != null;
  }

  private List<TrainingPlanContent> contents(Long trainingPlanId) {
    List<Long> stageIds = stageMapper.selectList(new LambdaQueryWrapper<TrainingPlanStage>()
                                                   .eq(TrainingPlanStage::getTrainingPlanId, trainingPlanId)
                                                   .orderByAsc(TrainingPlanStage::getSortOrder))
      .stream().map(TrainingPlanStage::getPlanStageId).toList();
    if (stageIds.isEmpty()) return List.of();
    return contentMapper.selectList(new LambdaQueryWrapper<TrainingPlanContent>()
                                      .in(TrainingPlanContent::getPlanStageId, stageIds)
                                      .orderByAsc(TrainingPlanContent::getSortOrder));
  }

  private List<TrainingPlanContentVO> listContents(Long trainingPlanId) {
    return contents(trainingPlanId).stream().map(content -> {
      TrainingPlanContentVO vo = new TrainingPlanContentVO();
      BeanUtils.copyProperties(content, vo);
      return vo;
    }).toList();
  }

  private TrainingPlanContent requireContent(Long trainingPlanId, Long planContentId) {
    TrainingPlanContent content = contentMapper.selectById(planContentId);
    if (content == null) throw notFound(8144, "培训计划内容不存在");
    TrainingPlanStage stage = stageMapper.selectById(content.getPlanStageId());
    if (stage == null || !trainingPlanId.equals(stage.getTrainingPlanId())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 8145, "计划内容不属于指定培训计划");
    }
    return content;
  }

  private TrainingExecutionLink findLink(Long contentId, String type) {
    return executionLinkMapper.selectOne(new LambdaQueryWrapper<TrainingExecutionLink>()
                                           .eq(TrainingExecutionLink::getPlanContentId, contentId)
                                           .eq(TrainingExecutionLink::getExecutionType, type));
  }

  private String learnerPlanCoverUrl(TrainingPlan plan) {
    Long coverFileId = typeCoverFileId(plan);
    return coverFileId == null ? null : "/api/training/learner/plans/" + plan.getTrainingPlanId() + "/cover";
  }

  private Long typeCoverFileId(TrainingPlan plan) {
    if (plan.getTrainingTypeId() == null) {
      return null;
    }
    TrainingType type = trainingTypeMapper.selectById(plan.getTrainingTypeId());
    return type == null ? null : type.getCoverFileId();
  }

  private boolean isLearnerVisiblePlan(TrainingPlan plan) {
    return LEARNER_VISIBLE_PLAN_STATUSES.contains(plan.getPublishStatus());
  }

  private void requireLearnerVisiblePlan(TrainingPlan plan) {
    if (!isLearnerVisiblePlan(plan)) {
      throw notFound(8155, "培训任务对当前学员不可见");
    }
  }

  private BusinessException notVisible() {
    return notFound(8155, "场次对当前学员不可见");
  }

  private TrainingPlan requirePlan(Long id) {
    TrainingPlan plan = planMapper.selectById(id);
    if (plan == null) throw notFound(8101, "培训计划不存在");
    return plan;
  }

  private TrainingParticipant requireParticipant(Long id) {
    TrainingParticipant participant = participantMapper.selectById(id);
    if (participant == null) throw notFound(8110, "参训人不存在");
    return participant;
  }

  private TrainingExecutionLinkVO toExecutionVO(TrainingExecutionLink link) {
    TrainingExecutionLinkVO vo = new TrainingExecutionLinkVO();
    BeanUtils.copyProperties(link, vo);
    return vo;
  }

  private TrainingParticipantVO toParticipantVO(TrainingParticipant participant) {
    TrainingParticipantVO vo = new TrainingParticipantVO();
    BeanUtils.copyProperties(participant, vo);
    return vo;
  }

  private TrainingRewardPenaltyVO toRewardVO(TrainingRewardPenalty record) {
    TrainingRewardPenaltyVO vo = new TrainingRewardPenaltyVO();
    BeanUtils.copyProperties(record, vo);
    return vo;
  }

  private BusinessException notFound(int code, String message) {
    return new BusinessException(HttpStatus.NOT_FOUND, code, message);
  }

  private BusinessException conflict(int code, String message) {
    return new BusinessException(HttpStatus.CONFLICT, code, message);
  }
}
