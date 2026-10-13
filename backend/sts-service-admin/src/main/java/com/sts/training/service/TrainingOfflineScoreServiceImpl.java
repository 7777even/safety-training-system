package com.sts.training.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.common.exception.BusinessException;
import com.sts.common.service.BaseServiceImpl;
import com.sts.training.dto.TrainingOfflineScoreQuery;
import com.sts.training.dto.TrainingOfflineScoreUpsertRequest;
import com.sts.training.entity.TrainingOfflineScore;
import com.sts.training.entity.TrainingOfflineSession;
import com.sts.training.entity.TrainingParticipant;
import com.sts.training.mapper.TrainingOfflineScoreMapper;
import com.sts.training.mapper.TrainingOfflineSessionMapper;
import com.sts.training.mapper.TrainingParticipantMapper;
import com.sts.training.port.TrainingResultRefresh;
import com.sts.training.vo.TrainingOfflineScoreVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class TrainingOfflineScoreServiceImpl
  extends BaseServiceImpl<TrainingOfflineScoreMapper, TrainingOfflineScore>
  implements TrainingOfflineScoreService {

  private final TrainingOfflineScoreMapper trainingOfflineScoreMapper;
  private final TrainingOfflineSessionMapper offlineSessionMapper;
  private final TrainingParticipantMapper participantMapper;
  private final TrainingResultRefresh trainingResultRefresh;

  @Override
  public PageResult<TrainingOfflineScoreVO> page(PageRequest<TrainingOfflineScoreQuery> request) {
    TrainingOfflineScoreQuery query = request.getQuery();
    LambdaQueryWrapper<TrainingOfflineScore> wrapper = new LambdaQueryWrapper<>();
    if (query != null) {
      wrapper.eq(query.getTrainingParticipantId() != null, TrainingOfflineScore::getTrainingParticipantId, query.getTrainingParticipantId())
        .eq(query.getOfflineSessionId() != null, TrainingOfflineScore::getOfflineSessionId, query.getOfflineSessionId())
        .eq(query.getScoreName() != null, TrainingOfflineScore::getScoreName, query.getScoreName());
    }
    wrapper.orderByDesc(TrainingOfflineScore::getScoredTime);
    Page<TrainingOfflineScore> page = trainingOfflineScoreMapper.selectPage(
      new Page<>(request.getPageNum(), request.getPageSize()), wrapper);
    return new PageResult<>(page.getRecords().stream().map(this::toVO).toList(), page.getTotal());
  }

  @Override
  @Transactional
  public TrainingOfflineScoreVO upsert(TrainingOfflineScoreUpsertRequest request, Long scoredBy, boolean adminActor) {
    TrainingOfflineSession session = offlineSessionMapper.selectById(request.getOfflineSessionId());
    if (session == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 8130, "线下场次不存在");
    }
    if (!Objects.equals(session.getTeacherUserId(), scoredBy)) {
      throw new BusinessException(HttpStatus.FORBIDDEN, 8131, "当前用户不是该场次教师");
    }
    if (!"IN_PROGRESS".equals(session.getSessionStatus())) {
      throw new BusinessException(HttpStatus.CONFLICT, 8203, "仅进行中的场次可录入成绩");
    }
    TrainingParticipant participant = participantMapper.selectById(request.getTrainingParticipantId());
    if (participant == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 8110, "参训人不存在");
    }
    if (!Objects.equals(participant.getTrainingPlanId(), session.getTrainingPlanId())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 8120, "参训人不属于指定计划");
    }
    TrainingOfflineScore existing = trainingOfflineScoreMapper.selectOne(new LambdaQueryWrapper<TrainingOfflineScore>()
      .eq(TrainingOfflineScore::getTrainingParticipantId, request.getTrainingParticipantId())
      .eq(TrainingOfflineScore::getOfflineSessionId, request.getOfflineSessionId())
                                                                           .orderByDesc(TrainingOfflineScore::getScoredTime)
                                                                           .last("LIMIT 1"));
    if (existing != null) {
      existing.setScoreName(request.getScoreName());
      existing.setScore(request.getScore());
      existing.setMaxScore(request.getMaxScore());
      existing.setScoreSource(request.getScoreSource());
      existing.setImportBatchId(request.getImportBatchId());
      existing.setScoredBy(scoredBy);
      existing.setScoredTime(request.getScoredTime());
      trainingOfflineScoreMapper.updateById(existing);
      trainingResultRefresh.refreshParticipantContent(
        request.getTrainingParticipantId(), session.getPlanContentId());
      return toVO(existing);
    }
    TrainingOfflineScore score = new TrainingOfflineScore();
    score.setTrainingParticipantId(request.getTrainingParticipantId());
    score.setOfflineSessionId(request.getOfflineSessionId());
    score.setScoreName(request.getScoreName());
    score.setScore(request.getScore());
    score.setMaxScore(request.getMaxScore());
    score.setScoreSource(request.getScoreSource());
    score.setImportBatchId(request.getImportBatchId());
    score.setScoredBy(scoredBy);
    score.setScoredTime(request.getScoredTime());
    trainingOfflineScoreMapper.insert(score);
    trainingResultRefresh.refreshParticipantContent(
      request.getTrainingParticipantId(), session.getPlanContentId());
    return toVO(score);
  }

  private TrainingOfflineScoreVO toVO(TrainingOfflineScore score) {
    TrainingOfflineScoreVO vo = new TrainingOfflineScoreVO();
    BeanUtils.copyProperties(score, vo);
    return vo;
  }
}
