package com.sts.training.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.training.dto.TrainingTeacherTaskQuery;
import com.sts.training.entity.TrainingOfflineScore;
import com.sts.training.entity.TrainingOfflineSession;
import com.sts.training.entity.TrainingParticipant;
import com.sts.training.entity.TrainingPlan;
import com.sts.training.mapper.TrainingOfflineScoreMapper;
import com.sts.training.mapper.TrainingOfflineSessionMapper;
import com.sts.training.mapper.TrainingParticipantMapper;
import com.sts.training.mapper.TrainingPlanMapper;
import com.sts.training.vo.TrainingTeacherTaskVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class TrainingTeacherTaskServiceImpl implements TrainingTeacherTaskService {

  private final TrainingOfflineSessionMapper offlineSessionMapper;
  private final TrainingParticipantMapper participantMapper;
  private final TrainingOfflineScoreMapper offlineScoreMapper;
  private final TrainingPlanMapper planMapper;

  @Override
  public PageResult<TrainingTeacherTaskVO> page(PageRequest<TrainingTeacherTaskQuery> request) {
    Long teacherUserId = StpUtil.getLoginIdAsLong();
    LambdaQueryWrapper<TrainingOfflineSession> wrapper = new LambdaQueryWrapper<TrainingOfflineSession>()
      .eq(TrainingOfflineSession::getTeacherUserId, teacherUserId)
      .orderByAsc(TrainingOfflineSession::getStartTime);
    Page<TrainingOfflineSession> page = offlineSessionMapper.selectPage(
      new Page<>(request.getPageNum(), request.getPageSize()), wrapper);
    List<Long> planIds = page.getRecords().stream().map(TrainingOfflineSession::getTrainingPlanId)
      .filter(Objects::nonNull).distinct().toList();
    Map<Long, String> planNameById = new HashMap<>();
    if (!planIds.isEmpty()) {
      planMapper.selectList(new LambdaQueryWrapper<TrainingPlan>().in(TrainingPlan::getTrainingPlanId, planIds))
        .forEach(plan -> planNameById.put(plan.getTrainingPlanId(), plan.getPlanName()));
    }
    return new PageResult<>(page.getRecords().stream()
      .map(session -> toTaskVO(session, planNameById.get(session.getTrainingPlanId()))).toList(), page.getTotal());
  }

  /** 将场次及其已批量读取的所属培训名称转换为教师任务摘要。 */
  private TrainingTeacherTaskVO toTaskVO(TrainingOfflineSession session, String trainingPlanName) {
    TrainingTeacherTaskVO vo = new TrainingTeacherTaskVO();
    vo.setOfflineSessionId(session.getOfflineSessionId());
    vo.setTrainingPlanId(session.getTrainingPlanId());
    vo.setTrainingPlanName(trainingPlanName);
    vo.setSessionName(session.getSessionName());
    vo.setLocation(session.getLocation());
    vo.setStartTime(session.getStartTime());
    vo.setEndTime(session.getEndTime());
    vo.setSessionStatus(session.getSessionStatus());
    vo.setVersion(session.getVersion());
    long participantCount = participantMapper.selectCount(new LambdaQueryWrapper<TrainingParticipant>()
                                                            .eq(TrainingParticipant::getTrainingPlanId,
                                                                session.getTrainingPlanId()));
    long scoredCount = offlineScoreMapper.selectCount(new LambdaQueryWrapper<TrainingOfflineScore>()
                                                        .eq(TrainingOfflineScore::getOfflineSessionId,
                                                            session.getOfflineSessionId())
                                                        .isNotNull(TrainingOfflineScore::getScoredBy));
    vo.setParticipantCount(participantCount);
    vo.setScoredCount(scoredCount);
    vo.setPendingScoreCount(Math.max(0, participantCount - scoredCount));
    return vo;
  }
}
