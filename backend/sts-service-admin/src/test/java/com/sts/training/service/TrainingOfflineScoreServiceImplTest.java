package com.sts.training.service;

import com.sts.training.dto.TrainingOfflineScoreUpsertRequest;
import com.sts.training.entity.TrainingOfflineScore;
import com.sts.training.entity.TrainingOfflineSession;
import com.sts.training.entity.TrainingParticipant;
import com.sts.training.mapper.TrainingOfflineScoreMapper;
import com.sts.training.mapper.TrainingOfflineSessionMapper;
import com.sts.training.mapper.TrainingParticipantMapper;
import com.sts.training.port.TrainingResultRefresh;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TrainingOfflineScoreServiceImplTest {

  private TrainingOfflineScoreMapper scoreMapper;
  private TrainingOfflineSessionMapper sessionMapper;
  private TrainingParticipantMapper participantMapper;
  private TrainingResultRefresh trainingResultRefresh;
  private TrainingOfflineScoreServiceImpl service;

  @BeforeEach
  void setUp() {
    scoreMapper = mock(TrainingOfflineScoreMapper.class);
    sessionMapper = mock(TrainingOfflineSessionMapper.class);
    participantMapper = mock(TrainingParticipantMapper.class);
    trainingResultRefresh = mock(TrainingResultRefresh.class);
    service = new TrainingOfflineScoreServiceImpl(
      scoreMapper, sessionMapper, participantMapper, trainingResultRefresh);
  }

  @Test
  @DisplayName("upsert：保存线下成绩后刷新投影")
  void upsert_insert_refreshesProjection() {
    TrainingOfflineSession session = new TrainingOfflineSession();
    session.setOfflineSessionId(10L);
    session.setTrainingPlanId(100L);
    session.setTeacherUserId(77L);
    session.setPlanContentId(30L);
    session.setSessionStatus("IN_PROGRESS");
    when(sessionMapper.selectById(10L)).thenReturn(session);
    TrainingParticipant participant = new TrainingParticipant();
    participant.setTrainingParticipantId(20L);
    participant.setTrainingPlanId(100L);
    when(participantMapper.selectById(20L)).thenReturn(participant);
    when(scoreMapper.selectOne(any())).thenReturn(null);
    when(scoreMapper.insert(any(TrainingOfflineScore.class))).thenReturn(1);

    TrainingOfflineScoreUpsertRequest req = new TrainingOfflineScoreUpsertRequest();
    req.setTrainingParticipantId(20L);
    req.setOfflineSessionId(10L);
    req.setScoreName("实操");
    req.setScore(new BigDecimal("80"));
    req.setMaxScore(new BigDecimal("100"));
    req.setScoreSource("MANUAL");
    req.setScoredTime(LocalDateTime.now());

    service.upsert(req, 77L, false);

    verify(trainingResultRefresh).refreshParticipantContent(20L, 30L);
  }

  @Test
  @DisplayName("upsert：同场同人不同成绩项名更新原行")
  void upsert_differentScoreName_updatesExistingRow() {
    TrainingOfflineSession session = new TrainingOfflineSession();
    session.setOfflineSessionId(10L);
    session.setTrainingPlanId(100L);
    session.setTeacherUserId(77L);
    session.setPlanContentId(30L);
    session.setSessionStatus("IN_PROGRESS");
    when(sessionMapper.selectById(10L)).thenReturn(session);
    TrainingParticipant participant = new TrainingParticipant();
    participant.setTrainingParticipantId(20L);
    participant.setTrainingPlanId(100L);
    when(participantMapper.selectById(20L)).thenReturn(participant);
    TrainingOfflineScore existing = new TrainingOfflineScore();
    existing.setOfflineScoreId(5L);
    existing.setScoreName("实操");
    existing.setTrainingParticipantId(20L);
    existing.setOfflineSessionId(10L);
    when(scoreMapper.selectOne(any())).thenReturn(existing);

    TrainingOfflineScoreUpsertRequest req = new TrainingOfflineScoreUpsertRequest();
    req.setTrainingParticipantId(20L);
    req.setOfflineSessionId(10L);
    req.setScoreName("考核");
    req.setScore(new BigDecimal("90"));
    req.setMaxScore(new BigDecimal("100"));
    req.setScoreSource("MANUAL");
    req.setScoredTime(LocalDateTime.now());

    service.upsert(req, 77L, false);

    verify(scoreMapper).updateById(existing);
    verify(scoreMapper, never()).insert(any(TrainingOfflineScore.class));
    assertThat(existing.getScoreName()).isEqualTo("考核");
    verify(trainingResultRefresh).refreshParticipantContent(20L, 30L);
  }

  @Test
  @DisplayName("upsert：非进行中场次拒绝录分")
  void upsert_scheduled_rejected() {
    TrainingOfflineSession session = new TrainingOfflineSession();
    session.setOfflineSessionId(10L);
    session.setTrainingPlanId(100L);
    session.setTeacherUserId(77L);
    session.setPlanContentId(30L);
    session.setSessionStatus("SCHEDULED");
    when(sessionMapper.selectById(10L)).thenReturn(session);

    TrainingOfflineScoreUpsertRequest req = new TrainingOfflineScoreUpsertRequest();
    req.setTrainingParticipantId(20L);
    req.setOfflineSessionId(10L);
    req.setScoreName("实操");
    req.setScore(new BigDecimal("80"));
    req.setMaxScore(new BigDecimal("100"));
    req.setScoreSource("MANUAL");
    req.setScoredTime(LocalDateTime.now());

    assertThatThrownBy(() -> service.upsert(req, 77L, false))
      .isInstanceOf(com.sts.common.exception.BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 8203);
    verify(scoreMapper, never()).insert(any(TrainingOfflineScore.class));
    verify(trainingResultRefresh, never()).refreshParticipantContent(any(), any());
  }
}
