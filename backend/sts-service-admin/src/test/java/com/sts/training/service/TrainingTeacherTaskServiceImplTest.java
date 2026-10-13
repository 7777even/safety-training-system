package com.sts.training.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sts.common.api.PageRequest;
import com.sts.training.dto.TrainingTeacherTaskQuery;
import com.sts.training.entity.TrainingOfflineSession;
import com.sts.training.entity.TrainingPlan;
import com.sts.training.mapper.TrainingOfflineScoreMapper;
import com.sts.training.mapper.TrainingOfflineSessionMapper;
import com.sts.training.mapper.TrainingParticipantMapper;
import com.sts.training.mapper.TrainingPlanMapper;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TrainingTeacherTaskServiceImplTest {

  @Test
  void page_derivesTeacherFromCurrentLoginInsteadOfQuery() {
    TrainingOfflineSessionMapper sessionMapper = mock(TrainingOfflineSessionMapper.class);
    TrainingTeacherTaskServiceImpl service = new TrainingTeacherTaskServiceImpl(
      sessionMapper, mock(TrainingParticipantMapper.class), mock(TrainingOfflineScoreMapper.class), mock(TrainingPlanMapper.class));
    PageRequest<TrainingTeacherTaskQuery> request = new PageRequest<>();
    request.setPageNum(1);
    request.setPageSize(20);
    request.setQuery(new TrainingTeacherTaskQuery());
    when(sessionMapper.selectPage(any(), any())).thenReturn(new Page<>(1, 20));

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(99L);

      assertThat(service.page(request).list()).isEmpty();

      stp.verify(StpUtil::getLoginIdAsLong);
      verify(sessionMapper).selectPage(any(), any());
    }
  }

  @Test
  void page_mapsSessionOptimisticLockVersion() {
    TrainingOfflineSessionMapper sessionMapper = mock(TrainingOfflineSessionMapper.class);
    TrainingParticipantMapper participantMapper = mock(TrainingParticipantMapper.class);
    TrainingOfflineScoreMapper scoreMapper = mock(TrainingOfflineScoreMapper.class);
    TrainingPlanMapper planMapper = mock(TrainingPlanMapper.class);
    TrainingTeacherTaskServiceImpl service = new TrainingTeacherTaskServiceImpl(
      sessionMapper, participantMapper, scoreMapper, planMapper);
    TrainingOfflineSession session = new TrainingOfflineSession();
    session.setOfflineSessionId(3L);
    session.setTrainingPlanId(20L);
    session.setSessionName("车间实操");
    session.setSessionStatus("SCHEDULED");
    session.setVersion(4);
    Page<TrainingOfflineSession> page = new Page<>(1, 20);
    page.setRecords(List.of(session));
    page.setTotal(1);
    when(sessionMapper.selectPage(any(), any())).thenReturn(page);
    when(participantMapper.selectCount(any())).thenReturn(2L);
    when(scoreMapper.selectCount(any())).thenReturn(0L);
    TrainingPlan plan = new TrainingPlan();
    plan.setTrainingPlanId(20L);
    plan.setPlanName("2026 年安全生产培训");
    when(planMapper.selectList(any())).thenReturn(List.of(plan));
    PageRequest<TrainingTeacherTaskQuery> request = new PageRequest<>();
    request.setPageNum(1);
    request.setPageSize(20);
    request.setQuery(new TrainingTeacherTaskQuery());

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(99L);

      var vo = service.page(request).list().getFirst();

      assertThat(vo.getVersion()).isEqualTo(4);
      assertThat(vo.getOfflineSessionId()).isEqualTo(3L);
      assertThat(vo.getTrainingPlanName()).isEqualTo("2026 年安全生产培训");
    }
  }

  @Test
  void page_keepsTaskWhenTrainingPlanIsMissing() {
    TrainingOfflineSessionMapper sessionMapper = mock(TrainingOfflineSessionMapper.class);
    TrainingParticipantMapper participantMapper = mock(TrainingParticipantMapper.class);
    TrainingOfflineScoreMapper scoreMapper = mock(TrainingOfflineScoreMapper.class);
    TrainingPlanMapper planMapper = mock(TrainingPlanMapper.class);
    TrainingTeacherTaskServiceImpl service = new TrainingTeacherTaskServiceImpl(
      sessionMapper, participantMapper, scoreMapper, planMapper);
    TrainingOfflineSession session = new TrainingOfflineSession();
    session.setOfflineSessionId(3L);
    session.setTrainingPlanId(20L);
    Page<TrainingOfflineSession> page = new Page<>(1, 20);
    page.setRecords(List.of(session));
    page.setTotal(1);
    when(sessionMapper.selectPage(any(), any())).thenReturn(page);
    when(participantMapper.selectCount(any())).thenReturn(0L);
    when(scoreMapper.selectCount(any())).thenReturn(0L);
    when(planMapper.selectList(any())).thenReturn(List.of());
    PageRequest<TrainingTeacherTaskQuery> request = new PageRequest<>();
    request.setPageNum(1);
    request.setPageSize(20);
    request.setQuery(new TrainingTeacherTaskQuery());

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(99L);

      var result = service.page(request);

      assertThat(result.list()).hasSize(1);
      assertThat(result.list().getFirst().getOfflineSessionId()).isEqualTo(3L);
      assertThat(result.list().getFirst().getTrainingPlanName()).isNull();
    }
  }
}
