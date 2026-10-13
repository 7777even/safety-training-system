package com.sts.exam.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sts.common.api.PageRequest;
import com.sts.exam.dto.ExamAttemptRecordQuery;
import com.sts.exam.dto.ExamReviewTaskQuery;
import com.sts.exam.entity.ExamAnticheatEvent;
import com.sts.exam.entity.ExamScorePublishLog;
import com.sts.exam.mapper.ExamAnticheatEventMapper;
import com.sts.exam.mapper.ExamAttemptQueryMapper;
import com.sts.exam.mapper.ExamScorePublishLogMapper;
import com.sts.exam.vo.ExamAttemptRecordVO;
import com.sts.exam.vo.ExamReviewTaskVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ExamAttemptQueryServiceImplTest {
  private MockedStatic<StpUtil> stpUtil;

  @AfterEach
  void tearDown() {
    if (stpUtil != null) stpUtil.close();
  }

  @Test
  void reviewTasks_alwaysRestrictCurrentReviewer() {
    ExamAttemptQueryMapper mapper = mock(ExamAttemptQueryMapper.class);
    when(mapper.selectReviewTaskPage(any(), eq(7L), isNull(), eq(false), any()))
      .thenReturn(new Page<ExamReviewTaskVO>(1, 20));
    stpUtil = mockStatic(StpUtil.class);
    stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(7L);

    var result = new ExamAttemptQueryServiceImpl(mapper)
      .pageReviewTasks(new PageRequest<>(1, 20, new ExamReviewTaskQuery()));

    assertThat(result.total()).isZero();
    verify(mapper).selectReviewTaskPage(any(), eq(7L), isNull(), eq(false), any());
  }

  /**
   * 可见范围改由租户插件在 SQL 层注入：Service 不再解析数据范围，
   * 也不再把机构集合传给 Mapper，只透传分页与查询条件。
   */
  @Test
  void attempts_delegateVisibilityToTenantPluginWithoutScopeResolution() {
    ExamAttemptQueryMapper mapper = mock(ExamAttemptQueryMapper.class);
    ExamAttemptRecordQuery query = new ExamAttemptRecordQuery();
    when(mapper.selectAttemptPage(any(), eq(query))).thenReturn(new Page<ExamAttemptRecordVO>(1, 20));

    var result = new ExamAttemptQueryServiceImpl(mapper)
      .pageAttempts(new PageRequest<>(1, 20, query));

    assertThat(result.total()).isZero();
    verify(mapper).selectAttemptPage(any(), eq(query));
    verifyNoMoreInteractions(mapper);
  }

  @Test
  void listAnticheatEvents_returnsEventsForAttempt() {
    ExamAnticheatEventMapper eventMapper = mock(ExamAnticheatEventMapper.class);
    ExamAnticheatEvent event = new ExamAnticheatEvent();
    event.setAnticheatEventId(3L);
    event.setExamAttemptId(100L);
    event.setEventType("SWITCH_AWAY");
    event.setEventCount(2);
    when(eventMapper.selectList(any())).thenReturn(java.util.List.of(event));

    var result = new ExamAttemptAuditQueryServiceImpl(eventMapper, mock(ExamScorePublishLogMapper.class))
      .listAnticheatEvents(100L);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().getEventType()).isEqualTo("SWITCH_AWAY");
    assertThat(result.getFirst().getEventCount()).isEqualTo(2);
  }

  @Test
  void listScorePublishLogs_returnsLogsForAttempt() {
    ExamScorePublishLogMapper logMapper = mock(ExamScorePublishLogMapper.class);
    ExamScorePublishLog log = new ExamScorePublishLog();
    log.setScorePublishLogId(5L);
    log.setExamAttemptId(100L);
    log.setPublishedBy(77L);
    log.setPublishType("MANUAL");
    log.setScoreSnapshot(new java.math.BigDecimal("88.00"));
    when(logMapper.selectList(any())).thenReturn(java.util.List.of(log));

    var result = new ExamAttemptAuditQueryServiceImpl(mock(ExamAnticheatEventMapper.class), logMapper)
      .listScorePublishLogs(100L);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().getPublishedBy()).isEqualTo(77L);
    assertThat(result.getFirst().getScoreSnapshot()).isEqualByComparingTo("88.00");
  }
}
