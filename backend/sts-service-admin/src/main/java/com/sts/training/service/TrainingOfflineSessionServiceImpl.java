package com.sts.training.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sts.admin.tenant.TenantContext;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.common.exception.BusinessException;
import com.sts.common.service.BaseServiceImpl;
import com.sts.training.dto.TrainingOfflineSessionCreateRequest;
import com.sts.training.dto.TrainingOfflineSessionQuery;
import com.sts.training.dto.TrainingOfflineSessionUpdateRequest;
import com.sts.training.entity.TrainingOfflineSession;
import com.sts.training.mapper.TrainingOfflineSessionMapper;
import com.sts.training.mapper.TrainingPlanMapper;
import com.sts.training.mapper.TrainingPlanStageMapper;
import com.sts.training.mapper.TrainingPlanContentMapper;
import com.sts.training.vo.TrainingOfflineSessionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class TrainingOfflineSessionServiceImpl
  extends BaseServiceImpl<TrainingOfflineSessionMapper, TrainingOfflineSession>
  implements TrainingOfflineSessionService {

  private final TrainingOfflineSessionMapper offlineSessionMapper;
  private final TrainingPlanMapper trainingPlanMapper;
  /** 创建安排时核对变更后的当前编排归属。 */
  private final TrainingPlanStageMapper stages;
  /** 锁后读取当前内容，防止引用已替换内容。 */
  private final TrainingPlanContentMapper contents;

  @Override
  public PageResult<TrainingOfflineSessionVO> page(PageRequest<TrainingOfflineSessionQuery> request) {
    TrainingOfflineSessionQuery query = request.getQuery();
    LambdaQueryWrapper<TrainingOfflineSession> wrapper = new LambdaQueryWrapper<>();
    if (query != null) {
      wrapper.eq(query.getTrainingPlanId() != null, TrainingOfflineSession::getTrainingPlanId, query.getTrainingPlanId())
        .eq(query.getPlanStageId() != null, TrainingOfflineSession::getPlanStageId, query.getPlanStageId())
        .eq(query.getTeacherUserId() != null, TrainingOfflineSession::getTeacherUserId, query.getTeacherUserId())
        .eq(query.getSessionStatus() != null, TrainingOfflineSession::getSessionStatus, query.getSessionStatus())
        .eq(query.getStatus() != null, TrainingOfflineSession::getStatus, query.getStatus());
    }
    wrapper.orderByDesc(TrainingOfflineSession::getStartTime);
    Page<TrainingOfflineSession> page = offlineSessionMapper.selectPage(
      new Page<>(request.getPageNum(), request.getPageSize()), wrapper);
    return new PageResult<>(page.getRecords().stream().map(this::toVO).toList(), page.getTotal());
  }

  @Override
  public TrainingOfflineSessionVO get(Long offlineSessionId) {
    return toVO(requireSession(offlineSessionId));
  }

  @Override
  @Transactional
  public Long create(TrainingOfflineSessionCreateRequest request) {
    requirePlan(request.getTrainingPlanId());
    if (request.getPlanStageId() != null) {
      var stage = stages.selectById(request.getPlanStageId());
      if (stage == null || !Objects.equals(stage.getTrainingPlanId(),request.getTrainingPlanId()))
        throw new BusinessException(HttpStatus.CONFLICT,8351,"原编排已变更，请刷新后创建线下安排");
    }
    if (request.getPlanContentId() != null) {
      var content = contents.selectById(request.getPlanContentId());
      var stage = content == null ? null : stages.selectById(content.getPlanStageId());
      if (stage == null || !Objects.equals(stage.getTrainingPlanId(),request.getTrainingPlanId())
        || (request.getPlanStageId() != null && !Objects.equals(content.getPlanStageId(),request.getPlanStageId()))
        || !"OFFLINE".equals(content.getContentType()))
        throw new BusinessException(HttpStatus.CONFLICT,8351,"原编排已变更，请刷新后创建线下安排");
    }
    TrainingOfflineSession session = new TrainingOfflineSession();
    session.setTrainingPlanId(request.getTrainingPlanId());
    session.setPlanStageId(request.getPlanStageId());
    session.setPlanContentId(request.getPlanContentId());
    session.setSessionName(request.getSessionName().trim());
    session.setTeacherUserId(request.getTeacherUserId());
    session.setLocation(request.getLocation());
    session.setStartTime(request.getStartTime());
    session.setEndTime(request.getEndTime());
    session.setSessionStatus("DRAFT");
    session.setStatus(request.getStatus());
    offlineSessionMapper.insert(session);
    return session.getOfflineSessionId();
  }

  @Override
  @Transactional
  public void update(Long offlineSessionId, TrainingOfflineSessionUpdateRequest request) {
    TrainingOfflineSession session = requireSession(offlineSessionId);
    session.setVersion(request.getVersion());
    session.setSessionName(request.getSessionName().trim());
    session.setTeacherUserId(request.getTeacherUserId());
    session.setLocation(request.getLocation());
    session.setStartTime(request.getStartTime());
    session.setEndTime(request.getEndTime());
    session.setStatus(request.getStatus());
    offlineSessionMapper.updateById(session);
  }

  @Override
  @Transactional
  public void start(Long offlineSessionId, Integer version, Long actorUserId, boolean adminActor) {
    transition(offlineSessionId, version, actorUserId, adminActor, List.of("SCHEDULED"), "IN_PROGRESS");
  }

  @Override
  @Transactional
  public void complete(Long offlineSessionId, Integer version, Long actorUserId, boolean adminActor) {
    transition(offlineSessionId, version, actorUserId, adminActor, List.of("IN_PROGRESS"), "COMPLETED");
  }

  @Override
  @Transactional
  public void cancel(Long offlineSessionId, Integer version, Long actorUserId, boolean adminActor) {
    transition(offlineSessionId, version, actorUserId, adminActor, List.of("SCHEDULED", "IN_PROGRESS"), "CANCELLED");
  }

  @Override
  @Transactional
  public void delete(Long offlineSessionId) {
    this.removeById(requireSession(offlineSessionId).getOfflineSessionId());
  }

  TrainingOfflineSession requireSession(Long id) {
    TrainingOfflineSession session = offlineSessionMapper.selectById(id);
    if (session == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 8201, "线下场次不存在");
    }
    return session;
  }

  private void transition(Long offlineSessionId, Integer version, Long actorUserId, boolean adminActor,
                          List<String> allowedFrom, String targetStatus) {
    TrainingOfflineSession session = requireSession(offlineSessionId);
    if (!adminActor && !Objects.equals(session.getTeacherUserId(), actorUserId)) {
      throw new BusinessException(HttpStatus.FORBIDDEN, 8131, "当前用户不是该场次教师");
    }
    if (!allowedFrom.contains(session.getSessionStatus())) {
      throw new BusinessException(HttpStatus.CONFLICT, 8202, "场次当前状态不允许此操作");
    }
    session.setVersion(version);
    session.setSessionStatus(targetStatus);
    if (offlineSessionMapper.updateById(session) == 0) {
      throw new BusinessException(HttpStatus.CONFLICT, 8204, "线下场次版本冲突");
    }
  }

  /** 与完整变更审批共享计划行锁，安排创建必须在编排替换前后串行。 */
  private void requirePlan(Long planId) {
    if (trainingPlanMapper.lockForApproval(planId,TenantContext.requireTenantId()) == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 8101, "培训计划不存在");
    }
  }

  private TrainingOfflineSessionVO toVO(TrainingOfflineSession session) {
    TrainingOfflineSessionVO vo = new TrainingOfflineSessionVO();
    BeanUtils.copyProperties(session, vo);
    return vo;
  }
}
